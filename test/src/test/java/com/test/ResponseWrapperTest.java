package com.test;

import com.skycong.httprawlog.wrapper.ResponseWrapper;
import jakarta.servlet.ServletOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ResponseWrapper 组件级测试：验证 tee 语义——客户端拿到完整响应，日志只旁路复制有限字节。
 */
public class ResponseWrapperTest {

    private static final int KB = 1024;

    /**
     * 默认上限 64KB：1MB 文本响应客户端拿全，日志只保留前 64KB 并置截断标记。
     */
    @Test
    void largeTextResponse_clientGetsAllBytes_logTruncatedAtDefault() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/plain;charset=UTF-8");
        ResponseWrapper wrapper = new ResponseWrapper(response);

        byte[] chunk = new byte[8192];
        Arrays.fill(chunk, (byte) 'A');
        int total = 1024 * KB;
        ServletOutputStream outputStream = wrapper.getOutputStream();
        for (int written = 0; written < total; written += chunk.length) {
            outputStream.write(chunk);
        }

        assertEquals(total, response.getContentAsByteArray().length);
        assertEquals(ResponseWrapper.DEFAULT_MAX_LOG_BODY_SIZE, wrapper.getCapturedLength());
        assertTrue(wrapper.isTruncated());
        assertEquals(total, wrapper.getTotalBytesWritten());
        assertFalse(wrapper.isCaptureSkipped());
    }

    /**
     * 上限可配置：构造时传入 4KB，日志只保留 4KB。
     */
    @Test
    void customMaxLogBodySize_isHonored() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/plain");
        ResponseWrapper wrapper = new ResponseWrapper(response, 4 * KB);

        wrapper.getOutputStream().write(new byte[8 * KB]);

        assertEquals(4 * KB, wrapper.getMaxLogBodySize());
        assertEquals(4 * KB, wrapper.getCapturedLength());
        assertTrue(wrapper.isTruncated());
        assertEquals(8 * KB, response.getContentAsByteArray().length);
    }

    /**
     * 二进制响应不捕获日志，但仍完整透传。
     */
    @Test
    void binaryResponse_notCaptured() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("application/octet-stream");
        ResponseWrapper wrapper = new ResponseWrapper(response);

        wrapper.getOutputStream().write(new byte[16 * KB]);

        assertEquals(16 * KB, response.getContentAsByteArray().length);
        assertEquals(0, wrapper.getCapturedLength());
        assertTrue(wrapper.isCaptureSkipped());
        assertTrue(wrapper.getCaptureSkipReason().contains("application/octet-stream"));
        assertEquals(16 * KB, wrapper.getTotalBytesWritten());
    }

    /**
     * 附件下载即使 Content-Type 是文本也不捕获。
     */
    @Test
    void attachmentResponse_notCaptured() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/plain");
        response.setHeader("Content-Disposition", "attachment; filename=report.txt");
        ResponseWrapper wrapper = new ResponseWrapper(response);

        wrapper.getOutputStream().write("secret".getBytes(StandardCharsets.UTF_8));

        assertEquals("secret", response.getContentAsString());
        assertEquals(0, wrapper.getCapturedLength());
        assertTrue(wrapper.isCaptureSkipped());
        assertTrue(wrapper.getCaptureSkipReason().contains("attachment"));
    }

    /**
     * gzip 压缩响应不捕获（字节不是可读文本）。
     */
    @Test
    void compressedResponse_notCaptured() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("application/json");
        response.setHeader("Content-Encoding", "gzip");
        ResponseWrapper wrapper = new ResponseWrapper(response);

        wrapper.getOutputStream().write(new byte[1024]);

        assertEquals(0, wrapper.getCapturedLength());
        assertTrue(wrapper.isCaptureSkipped());
        assertTrue(wrapper.getCaptureSkipReason().contains("gzip"));
    }

    /**
     * SSE 是流式响应，不做 body 捕获。
     */
    @Test
    void sseResponse_notCaptured() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/event-stream");
        ResponseWrapper wrapper = new ResponseWrapper(response);

        wrapper.getOutputStream().write("data: hello\n\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(0, wrapper.getCapturedLength());
        assertTrue(wrapper.isCaptureSkipped());
    }

    /**
     * getWriter() 必须返回同一个实例，且 flush 后客户端和日志都能拿到完整字符。
     */
    @Test
    void writer_isSingletonAndFlushed() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/plain;charset=UTF-8");
        ResponseWrapper wrapper = new ResponseWrapper(response);

        PrintWriter writer = wrapper.getWriter();
        writer.write("你好");
        assertSame(writer, wrapper.getWriter());

        wrapper.flushWriter();

        assertEquals("你好", response.getContentAsString());
        assertEquals("你好", new String(wrapper.getCapturedBuffer(), 0, wrapper.getCapturedLength(), StandardCharsets.UTF_8));
        assertFalse(wrapper.isTruncated());
    }

    /**
     * getWriter()/getOutputStream() 互斥，符合 Servlet 规范。
     */
    @Test
    void writerAndOutputStream_areMutuallyExclusive() throws Exception {
        ResponseWrapper writerFirst = new ResponseWrapper(new MockHttpServletResponse());
        writerFirst.getWriter();
        assertThrows(IllegalStateException.class, writerFirst::getOutputStream);

        ResponseWrapper streamFirst = new ResponseWrapper(new MockHttpServletResponse());
        streamFirst.getOutputStream();
        assertThrows(IllegalStateException.class, streamFirst::getWriter);
    }

    /**
     * resetBuffer 后日志缓冲应同步清空，避免把已丢弃的内容写进日志。
     */
    @Test
    void resetBuffer_clearsCapturedLog() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/plain");
        ResponseWrapper wrapper = new ResponseWrapper(response);
        wrapper.getOutputStream().write("discarded".getBytes(StandardCharsets.UTF_8));

        wrapper.resetBuffer();

        assertEquals(0, wrapper.getCapturedLength());
        assertEquals(0, wrapper.getTotalBytesWritten());
        assertFalse(wrapper.isTruncated());
        assertFalse(wrapper.isCaptureSkipped());
    }

    /**
     * 没有响应 body 时不预分配 8KB 捕获缓冲。
     */
    @Test
    void emptyResponse_hasNoPreallocatedCaptureBuffer() {
        ResponseWrapper wrapper = new ResponseWrapper(new MockHttpServletResponse());

        assertEquals(0, wrapper.getCapturedBuffer().length);
        assertEquals(0, wrapper.getCapturedLength());
    }

    /**
     * sendError 应记录状态/消息并清空已写 body，避免把错误页之外的旧内容写进日志。
     */
    @Test
    void sendError_recordsSummaryAndClearsBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setContentType("text/plain");
        ResponseWrapper wrapper = new ResponseWrapper(response);
        wrapper.getOutputStream().write("old-body".getBytes(StandardCharsets.UTF_8));

        wrapper.sendError(404, "not found");

        assertTrue(wrapper.isErrorSent());
        assertEquals("[sendError 404: not found]", wrapper.getErrorSummary());
        assertEquals(0, wrapper.getCapturedLength());
        assertEquals(0, wrapper.getTotalBytesWritten());
        assertEquals(404, response.getStatus());
    }
}
