package com.test;

import com.skycong.httprawlog.wrapper.RequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RequestWrapper 组件级测试：直接构造 MockHttpServletRequest 包装，验证
 * “回放给业务的 body 始终完整”与“日志字符串按 4MB 截断”的契约，不依赖 Spring 容器，运行快。
 */
public class RequestWrapperBodyTest {

    private static final int FOUR_MB = 4 * 1024 * 1024;

    /**
     * 5MB 请求体：源头截断到 4MB，getInputStream() 回放截断后的内容。
     */
    @Test
    void largeBody_truncatedAtSource() throws IOException {
        byte[] body = filled(FOUR_MB + 1024 * 1024, (byte) 'A');
        RequestWrapper wrapper = wrap(body, "application/json");

        // 业务回放 body 被截断到 4MB（源头截断，防 OOM）
        byte[] replayed = StreamUtils.copyToByteArray(wrapper.getInputStream());
        assertEquals(FOUR_MB, replayed.length);

        // 日志也是截断后的内容
        String log = wrapper.getRequestBodyLogString();
        assertEquals(FOUR_MB, log.length());
    }

    /**
     * 1KB 请求体：回放完整，日志完整且无截断标记。
     */
    @Test
    void smallBody_logNotTruncated() throws IOException {
        String body = repeat('B', 1024);
        RequestWrapper wrapper = wrap(body.getBytes(StandardCharsets.US_ASCII), "application/json");

        byte[] replayed = StreamUtils.copyToByteArray(wrapper.getInputStream());
        assertEquals(body, new String(replayed, StandardCharsets.US_ASCII));

        String log = wrapper.getRequestBodyLogString();
        assertFalse(log.contains("request body truncated"));
        assertEquals(body, log);
    }

    /**
     * 边界：4MB + 1 字节，应被截断。
     */
    @Test
    void boundary_fourMbPlusOne_truncated() {
        RequestWrapper wrapper = wrap(filled(FOUR_MB + 1, (byte) 'C'), "application/json");
        assertTrue(wrapper.getRequestBodyLogString().contains("[request body truncated"));
    }

    /**
     * 边界：正好 4MB，不应被截断。
     */
    @Test
    void boundary_fourMb_notTruncated() {
        RequestWrapper wrapper = wrap(filled(FOUR_MB, (byte) 'D'), "application/json");
        assertFalse(wrapper.getRequestBodyLogString().contains("request body truncated"));
    }

    /**
     * application/x-www-form-urlencoded：URL 解码后可读，小 body 不截断。
     */
    @Test
    void formUrlEncoded_decodedAndNotTruncated() {
        String body = "name=%E5%BC%A0%E4%B8%89&age=18";
        RequestWrapper wrapper = wrap(body.getBytes(StandardCharsets.UTF_8), "application/x-www-form-urlencoded");
        String log = wrapper.getRequestBodyLogString();
        assertTrue(log.contains("张三"));
        assertFalse(log.contains("request body truncated"));
    }

    /**
     * 空请求体：日志字符串为空。
     */
    @Test
    void emptyBody_returnsEmpty() {
        RequestWrapper wrapper = wrap(new byte[0], "application/json");
        assertEquals("", wrapper.getRequestBodyLogString());
    }

    private static RequestWrapper wrap(byte[] content, String contentType) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setContentType(contentType);
        req.setContent(content);
        return new RequestWrapper(req, 0);
    }

    private static byte[] filled(int n, byte b) {
        byte[] arr = new byte[n];
        for (int i = 0; i < n; i++) {
            arr[i] = b;
        }
        return arr;
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
