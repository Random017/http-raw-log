package com.test;

import com.skycong.httprawlog.api.History;
import com.skycong.httprawlog.api.HistoryStore;
import com.skycong.httprawlog2.HttpRawLogApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 真实容器下 form-urlencoded 请求：容器会消费 body 解析参数，
 * 这里验证原始 body 仍能被缓存，同时 @RequestParam 仍可用。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = HttpRawLogApplication.class)
@ComponentScan("com.skycong.httprawlog2")
public class FormUrlEncodedIntegrationTest {

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    HistoryStore historyStore;

    @Test
    void formBody_rawBodyAndParametersBothWork() throws Exception {
        String body = "name=%E5%BC%A0%E4%B8%89&age=18";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<byte[]> response = restTemplate.postForEntity("/test/form",
                new HttpEntity<>(body.getBytes(StandardCharsets.US_ASCII), headers), byte[].class);
        assertEquals(200, response.getStatusCode().value());

        String json = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"name\":\"张三\""), "form parameter should be decoded, actual: " + json);

        String hLogId = response.getHeaders().getFirst("hLogId");
        assertNotNull(hLogId, "filter should set hLogId response header");
        History history = awaitHistory(hLogId);
        assertTrue(history.getRequestBody().contains("name=张三"),
                "raw form body should be readable in history, actual: " + history.getRequestBody());
        assertTrue(history.getRequestBody().contains("age=18"),
                "raw form body should contain all fields, actual: " + history.getRequestBody());
    }

    /**
     * 显式 GBK 的 form：@RequestParam 与日志解码都必须用同一个 charset，
     * 不能出现“参数正确、日志乱码”。
     */
    @Test
    void gbkForm_parametersAndLogBothUseDeclaredCharset() throws Exception {
        String body = "name=%D5%C5%C8%FD&age=18";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-www-form-urlencoded;charset=GBK"));

        ResponseEntity<byte[]> response = restTemplate.postForEntity("/test/form",
                new HttpEntity<>(body.getBytes(StandardCharsets.US_ASCII), headers), byte[].class);
        assertEquals(200, response.getStatusCode().value());

        String json = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"name\":\"张三\""), "GBK form parameter should be decoded, actual: " + json);

        String hLogId = response.getHeaders().getFirst("hLogId");
        assertNotNull(hLogId, "filter should set hLogId response header");
        History history = awaitHistory(hLogId);
        assertTrue(history.getRequestBody().contains("name=张三"),
                "GBK raw form body should be decoded with charset=GBK, actual: " + history.getRequestBody());
    }

    /**
     * 没有 '=' 的 flag 参数：required @RequestParam 应收到空字符串，不能被当成参数缺失而返回 400。
     */
    @Test
    void formFlagWithoutEquals_requiredRequestParamReceivesEmptyString() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        ResponseEntity<byte[]> response = restTemplate.postForEntity("/test/formFlag",
                new HttpEntity<>("flag".getBytes(StandardCharsets.US_ASCII), headers), byte[].class);
        assertEquals(200, response.getStatusCode().value());

        String json = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"flag\":\"\""), "flag should be empty string, actual: " + json);
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
