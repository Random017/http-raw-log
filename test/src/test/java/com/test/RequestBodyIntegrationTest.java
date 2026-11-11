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
 * 请求体大小限制的集成测试：走真实的 HttpRawLogFilter + HistoryApi 全链路。
 *
 * <p>验证核心契约：
 * <ol>
 *   <li>超过 {@code maxRequestBodyBytes}（默认 64KB）的请求不再被包装，业务侧仍然收到完整 body；</li>
 *   <li>日志只写一行 skipped 标记，包含 content-length 与上限，避免 2GB Content-Length 触发 OOM；</li>
 *   <li>小 body 继续完整记录。</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = HttpRawLogApplication.class)
@ComponentScan("com.skycong.httprawlog2")
@AutoConfigureMockMvc
public class RequestBodyIntegrationTest {

    private static final int SIXTY_FOUR_KB = 64 * 1024;
    private static final int FIVE_MB = 5 * 1024 * 1024;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    /**
     * 大请求体（5MB）：业务侧完整拿到 5MB；日志侧不缓存 body，只记录 skipped 标记。
     */
    @Test
    void largeBody_businessComplete_logSkipped() throws Exception {
        String body = repeat('A', FIVE_MB);
        byte[] content = body.getBytes(StandardCharsets.US_ASCII);

        MvcResult result = mockMvc.perform(post("/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andReturn();

        // 1) 业务侧拿到完整 5MB
        assertEquals(FIVE_MB, com.skycong.httprawlog2.EchoTestController.lastReceivedBody.length());

        // 2) 日志侧只记录跳过原因，不再把 5MB 读进内存
        String hLogId = result.getResponse().getHeader("hLogId");
        assertNotNull(hLogId);
        String requestBody = requestBodyOf(hLogId);
        assertNotNull(requestBody);
        assertTrue(requestBody.startsWith("[request body skipped: content-length=" + FIVE_MB),
                "unexpected request body log: " + requestBody);
        assertTrue(requestBody.contains("maxRequestBodyBytes=" + SIXTY_FOUR_KB));
    }

    /**
     * 声明的 Content-Length 为 2GB 但实际没有 body：filter 不能按声明长度分配数组。
     */
    @Test
    void hugeDeclaredContentLength_doesNotAllocate() throws Exception {
        int declared = Integer.MAX_VALUE;
        MvcResult result = mockMvc.perform(post("/test/echoBytes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Content-Length", Integer.toString(declared))
                        .content(new byte[0]))
                .andExpect(status().isOk())
                .andReturn();

        String requestBody = requestBodyOf(result.getResponse().getHeader("hLogId"));
        assertNotNull(requestBody);
        assertTrue(requestBody.contains("content-length=" + declared), "unexpected request body log: " + requestBody);
        assertTrue(requestBody.contains("maxRequestBodyBytes=" + SIXTY_FOUR_KB));
    }

    /**
     * 小请求体（1KB）：业务侧完整收到，日志 requestBody 也完整、无截断/跳过标记。
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
        assertFalse(requestBody.contains("request body skipped"), "小 body 不应被跳过");
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
