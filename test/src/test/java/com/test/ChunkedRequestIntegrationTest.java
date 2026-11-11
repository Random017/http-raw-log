package com.test;

import com.skycong.httprawlog.api.History;
import com.skycong.httprawlog.api.HistoryStore;
import com.skycong.httprawlog2.HttpRawLogApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.ComponentScan;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 真实容器 chunked（无 Content-Length）请求测试。
 *
 * <p>小 body：日志完整、业务完整；大 body：日志截断，但业务仍拿到完整 body。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = HttpRawLogApplication.class,
        properties = "com.skycong.http-raw.log.history.maxBodyBytes=131072")
@ComponentScan("com.skycong.httprawlog2")
public class ChunkedRequestIntegrationTest {

    private static final int MAX_REQUEST_BODY = 64 * 1024;

    @LocalServerPort
    int port;

    @Autowired
    HistoryStore historyStore;

    @Test
    void smallChunkedBody_isFullyLoggedAndReadable() throws Exception {
        String body = "chunked-body-中文";
        Result result = postChunked("text/plain;charset=UTF-8", body.getBytes(StandardCharsets.UTF_8));

        assertEquals(200, result.status);
        assertEquals(body, com.skycong.httprawlog2.EchoTestController.lastReceivedBody);
        assertNotNull(result.hLogId);

        History history = awaitHistory(result.hLogId);
        assertEquals(body, history.getRequestBody());
        assertFalse(history.getRequestBody().contains("truncated"));
    }

    @Test
    void oversizedChunkedBody_businessGetsAll_logIsTruncated() throws Exception {
        byte[] body = new byte[MAX_REQUEST_BODY + 16 * 1024];
        java.util.Arrays.fill(body, (byte) 'A');
        Result result = postChunked("text/plain", body);

        assertEquals(200, result.status);
        assertEquals(body.length, com.skycong.httprawlog2.EchoTestController.lastReceivedBody.length(),
                "business must receive the complete chunked body");
        assertNotNull(result.hLogId);

        History history = awaitHistory(result.hLogId);
        assertTrue(history.getRequestBody().startsWith("A"),
                "log should keep the prefix, actual: " + history.getRequestBody());
        assertTrue(history.getRequestBody().contains("request body truncated"),
                "large chunked body should be marked truncated, actual: " + history.getRequestBody());
    }

    private Result postChunked(String contentType, byte[] body) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)
                URI.create("http://127.0.0.1:" + port + "/test/echo").toURL().openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setChunkedStreamingMode(0);
        connection.setRequestProperty("Content-Type", contentType);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }
        int status = connection.getResponseCode();
        String hLogId = connection.getHeaderField("hLogId");
        try (InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
            if (input != null) {
                input.readAllBytes();
            }
        } finally {
            connection.disconnect();
        }
        return new Result(status, hLogId);
    }

    private History awaitHistory(String hLogId) throws Exception {
        for (int i = 0; i < 50; i++) {
            java.util.List<History> histories = historyStore.findByLogId(hLogId);
            if (!histories.isEmpty()) {
                return histories.get(0);
            }
            Thread.sleep(100);
        }
        fail("history not recorded for hLogId=" + hLogId + " within 5s");
        return null;
    }

    private static final class Result {
        private final int status;
        private final String hLogId;

        private Result(int status, String hLogId) {
            this.status = status;
            this.hLogId = hLogId;
        }
    }
}
