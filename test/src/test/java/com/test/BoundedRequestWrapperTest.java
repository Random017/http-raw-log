package com.test;

import com.skycong.httprawlog.wrapper.BoundedRequestWrapper;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 未知 Content-Length（chunked）请求体包装测试。
 */
public class BoundedRequestWrapperTest {

    @Test
    void smallBody_isFullyBufferedAndReplayed() throws Exception {
        String body = "{\"name\":\"chunked\"}";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));

        BoundedRequestWrapper wrapper = new BoundedRequestWrapper(request, 1024);

        assertFalse(wrapper.isTruncated());
        assertEquals(body, wrapper.getRequestBodyLogString());
        assertEquals(body, new String(StreamUtils.copyToByteArray(wrapper.getInputStream()), StandardCharsets.UTF_8));
    }

    @Test
    void oversizedBody_logTruncated_butBusinessCanReadAll() throws Exception {
        String body = "0123456789";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("text/plain");
        request.setContent(body.getBytes(StandardCharsets.US_ASCII));
        request.setQueryString("a=1");

        BoundedRequestWrapper wrapper = new BoundedRequestWrapper(request, 4);

        assertTrue(wrapper.isTruncated());
        assertEquals("0123\n... [request body truncated: unknown/chunked length, only first 4 bytes logged]",
                wrapper.getRequestBodyLogString());
        assertEquals(body, new String(StreamUtils.copyToByteArray(wrapper.getInputStream()),
                StandardCharsets.US_ASCII));
        assertEquals("1", wrapper.getParameter("a"));
    }

    /**
     * 大上限 + 小 body 不能按上限预分配；否则 maxRequestBodyBytes=2GB 时会直接 OOM。
     */
    @Test
    void hugeLimitWithTinyBody_doesNotPreallocateLimit() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("text/plain");
        request.setContent("x".getBytes(StandardCharsets.US_ASCII));

        BoundedRequestWrapper wrapper = new BoundedRequestWrapper(request, Integer.MAX_VALUE - 2);

        assertFalse(wrapper.isTruncated());
        assertEquals("x", wrapper.getRequestBodyLogString());
    }

    /**
     * 非截断模式也返回同一个流实例，避免同一 wrapper 两种读取语义。
     */
    @Test
    void fullBody_getInputStreamReturnsSameConsumedStream() throws Exception {
        String body = "hello";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("text/plain");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));

        BoundedRequestWrapper wrapper = new BoundedRequestWrapper(request, 1024);
        ServletInputStream first = wrapper.getInputStream();

        assertSame(first, wrapper.getInputStream());
        assertEquals(body, new String(first.readAllBytes(), StandardCharsets.UTF_8));
        assertSame(first, wrapper.getInputStream());
        assertEquals(-1, first.read());
    }

    /**
     * 截断模式同样缓存同一个流，第二次 getInputStream() 不会变成空流。
     */
    @Test
    void truncatedBody_getInputStreamReturnsSameConsumedStream() throws Exception {
        String body = "0123456789";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("text/plain");
        request.setContent(body.getBytes(StandardCharsets.US_ASCII));

        BoundedRequestWrapper wrapper = new BoundedRequestWrapper(request, 4);
        ServletInputStream first = wrapper.getInputStream();

        assertSame(first, wrapper.getInputStream());
        assertEquals(body, new String(first.readAllBytes(), StandardCharsets.US_ASCII));
        assertSame(first, wrapper.getInputStream());
        assertEquals(-1, first.read());
    }
}
