package com.test;

import com.skycong.httprawlog.api.History;
import com.skycong.httprawlog.api.HistoryStore;
import com.skycong.httprawlog2.HttpRawLogApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 真实 Servlet 容器下的响应字符集测试。
 *
 * <p>Tomcat 对没有 charset 的响应调用 {@code getCharacterEncoding()} 会得到 ISO-8859-1，
 * 但 Spring 的 JSON 序列化实际写 UTF-8。本测试确保日志和历史不会变成乱码。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = HttpRawLogApplication.class)
@ComponentScan("com.skycong.httprawlog2")
public class ResponseCharsetIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    HistoryStore historyStore;

    @Test
    void jsonWithoutCharset_isLoggedAsUtf8() throws Exception {
        ResponseEntity<byte[]> response = restTemplate.getForEntity("/test/jsonNoCharset", byte[].class);
        assertEquals(200, response.getStatusCode().value());

        String contentType = response.getHeaders().getFirst("Content-Type");
        assertNotNull(contentType, "response should have Content-Type");
        assertTrue(contentType.toLowerCase(Locale.ROOT).startsWith("application/json"), contentType);
        assertFalse(contentType.toLowerCase(Locale.ROOT).contains("charset"),
                "this test must reproduce the no-charset scenario, actual: " + contentType);

        String responseBody = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(responseBody.contains("中文JSON响应"), "client response body: " + responseBody);

        String hLogId = response.getHeaders().getFirst("hLogId");
        assertNotNull(hLogId, "filter should set hLogId response header");

        History history = awaitHistory(hLogId);
        assertTrue(history.getResponseBody().contains("中文JSON响应"),
                "history responseBody should be UTF-8 decoded, actual: " + history.getResponseBody());
        assertFalse(history.getResponseBody().contains("盲赂"),
                "history responseBody must not contain mojibake: " + history.getResponseBody());
        assertTrue(history.getResponseHeaders().contains("content-type="),
                "response headers should contain content-type, actual: " + history.getResponseHeaders());
        assertEquals("", history.getRequestBody(),
                "GET without body must not be marked as skipped: " + history.getRequestBody());
    }

    @Test
    void headWithoutBody_isNotMarkedSkipped() throws Exception {
        HttpHeaders headers = restTemplate.headForHeaders("/test/jsonNoCharset");
        String hLogId = headers.getFirst("hLogId");
        assertNotNull(hLogId, "filter should set hLogId response header");

        History history = awaitHistory(hLogId);
        assertEquals("", history.getRequestBody(),
                "HEAD without body must not be marked as skipped: " + history.getRequestBody());
    }

    private History awaitHistory(String hLogId) throws Exception {
        for (int i = 0; i < 50; i++) {
            List<History> histories = historyStore.findByLogId(hLogId);
            if (!histories.isEmpty()) {
                return histories.get(0);
            }
            Thread.sleep(100);
        }
        fail("history not recorded for hLogId=" + hLogId + " within 5s");
        return null;
    }
}
