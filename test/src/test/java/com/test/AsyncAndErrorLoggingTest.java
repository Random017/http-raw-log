package com.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.skycong.httprawlog2.HttpRawLogApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 异步请求与异常路径的日志记录集成测试（真实 Servlet 容器，非 MockMvc）。
 *
 * <ul>
 *   <li>Callable 异步请求：filter 在 onComplete 时记录，而不是在初始 dispatch 返回时记录；</li>
 *   <li>Controller 抛异常：仍然记录，状态为 500，responseBody 带异常摘要。</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = HttpRawLogApplication.class)
@ComponentScan("com.skycong.httprawlog2")
public class AsyncAndErrorLoggingTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    TestRestTemplate restTemplate;

    /**
     * Callable 异步请求在真正完成后才写历史，且只写一条。
     */
    @Test
    void callableAsync_isLoggedOnCompletion() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/test/getCall/asyncPath?abc=asyncValue", String.class);
        assertEquals(200, response.getStatusCode().value());
        String hLogId = response.getHeaders().getFirst("hLogId");
        assertNotNull(hLogId, "filter should set hLogId response header");

        Map<String, Object> history = awaitHistory(hLogId);
        assertEquals("200", String.valueOf(history.get("responseStatus")));
        assertEquals("/test/getCall/asyncPath", history.get("url"));
        assertTrue(String.valueOf(history.get("responseBody")).contains("asyncPath"));
    }

    /**
     * Controller 异常：日志必须记录 500 + 异常类/消息，而不是默认的 200。
     */
    @Test
    void controllerException_isLoggedAs500() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/test/error", String.class);
        assertEquals(500, response.getStatusCode().value());
        String hLogId = response.getHeaders().getFirst("hLogId");
        assertNotNull(hLogId, "filter should set hLogId response header");

        Map<String, Object> history = awaitHistory(hLogId);
        assertEquals("500", String.valueOf(history.get("responseStatus")));
        assertEquals("/test/error", history.get("url"));
        String responseBody = String.valueOf(history.get("responseBody"));
        assertTrue(responseBody.contains("[exception: java.lang.IllegalStateException"),
                "responseBody should contain exception summary, actual: " + responseBody);
        assertTrue(responseBody.contains("boom-for-test"));
    }

    /**
     * 显式 sendError：即使错误页正文由容器渲染，日志也要包含状态与消息。
     */
    @Test
    void sendError_isLoggedWithStatusAndMessage() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity("/test/sendError", String.class);
        assertEquals(404, response.getStatusCode().value());
        String hLogId = response.getHeaders().getFirst("hLogId");
        assertNotNull(hLogId, "filter should set hLogId response header");

        Map<String, Object> history = awaitHistory(hLogId);
        assertEquals("404", String.valueOf(history.get("responseStatus")));
        assertEquals("/test/sendError", history.get("url"));
        String responseBody = String.valueOf(history.get("responseBody"));
        assertTrue(responseBody.contains("[sendError 404: widget not found]"),
                "responseBody should contain sendError summary, actual: " + responseBody);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> awaitHistory(String hLogId) throws Exception {
        for (int i = 0; i < 50; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    "/httpRawLog/history?hLogId=" + hLogId, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> histories = objectMapper.readValue(response.getBody(), List.class);
                if (!histories.isEmpty()) {
                    return histories.get(0);
                }
            }
            Thread.sleep(100);
        }
        fail("history not recorded for hLogId=" + hLogId + " within 5s");
        return null;
    }
}
