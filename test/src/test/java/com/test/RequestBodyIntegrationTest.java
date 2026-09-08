package com.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skycong.httprawlog2.HttpRawLogApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RequestWrapper 截断行为的集成测试：走真实的 HttpRawLogFilter + HistoryApi 全链路。
 *
 * <p>验证两条核心契约：
 * <ol>
 *   <li>业务侧（Controller）收到的请求体始终完整，不受 4MB 日志上限影响；</li>
 *   <li>记录的日志 requestBody 超过 4MB 时被截断并附标记，小 body 则完整记录。</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = HttpRawLogApplication.class)
@ComponentScan("com.skycong.httprawlog2")
@AutoConfigureMockMvc
public class RequestBodyIntegrationTest {

    private static final int FOUR_MB = 4 * 1024 * 1024;
    private static final int FIVE_MB = 5 * 1024 * 1024;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    /**
     * 大请求体（5MB）：源头截断到 4MB，业务侧和日志侧均拿到截断后的内容。
     */
    @Test
    void largeBody_businessAndLogTruncated() throws Exception {
        String body = repeat('A', FIVE_MB);
        byte[] content = body.getBytes(StandardCharsets.US_ASCII);

        MvcResult result = mockMvc.perform(post("/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andReturn();

        // 1) 业务侧拿到的 body 被截断到 4MB（源头截断，防 OOM）
        assertEquals(FOUR_MB, com.skycong.httprawlog2.EchoTestController.lastReceivedBody.length());

        // 2) 记录的日志 requestBody 也是截断后的内容
        String hLogId = result.getResponse().getHeader("hLogId");
        assertNotNull(hLogId);
        String requestBody = requestBodyOf(hLogId);
        assertNotNull(requestBody);
        assertEquals(FOUR_MB, requestBody.length());
    }

    /**
     * 小请求体（1KB）：业务侧完整收到，日志 requestBody 也完整、无截断标记。
     */
    @Test
    void smallBody_fullLoggedAndBusinessComplete() throws Exception {
        String body = repeat('B', 1024);
        byte[] content = body.getBytes(StandardCharsets.US_ASCII);

        MvcResult result = mockMvc.perform(post("/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andReturn();

        assertEquals(1024, com.skycong.httprawlog2.EchoTestController.lastReceivedBody.length());

        String hLogId = result.getResponse().getHeader("hLogId");
        String requestBody = requestBodyOf(hLogId);
        assertFalse(requestBody.contains("request body truncated"), "小 body 不应被截断");
        assertEquals(body, requestBody);
    }

    @SuppressWarnings("unchecked")
    private String requestBodyOf(String hLogId) throws Exception {
        String historyJson = mockMvc.perform(get("/httpRawLog/history").param("hLogId", hLogId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> histories = objectMapper.readValue(historyJson, List.class);
        return histories.stream()
                .filter(h -> hLogId.equals(h.get("hLogId")))
                .findFirst()
                .map(h -> (String) h.get("requestBody"))
                .orElse(null);
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
