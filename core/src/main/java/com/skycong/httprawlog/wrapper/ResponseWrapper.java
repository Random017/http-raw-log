package com.skycong.httprawlog.wrapper;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * 对 response 包装：业务写入直接透传给客户端（tee 语义），同时旁路复制前 {@link #maxLogBodySize} 字节用于日志。
 *
 * <p>与旧实现的关键区别：
 * <ul>
 *   <li>不再把整个响应缓存在内存里，超过日志上限的响应仍会完整返回给客户端；</li>
 *   <li>首次写入时根据 Content-Type / Content-Disposition / Content-Encoding 判断是否值得记录，
 *       文件下载、图片、视频、SSE 等二进制/流式响应不再白拷日志缓冲；</li>
 *   <li>{@code getWriter()} 返回同一个 writer，并由 filter 在请求结束时 flush，避免视图渲染丢字符；</li>
 *   <li>{@code getWriter()/getOutputStream()} 互斥语义与 Servlet 规范一致。</li>
 * </ul>
 *
 * @author ruanmingcong
 * @since 2022/8/11 15:47
 */
public class ResponseWrapper extends HttpServletResponseWrapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResponseWrapper.class);

    /**
     * 默认日志最大字节数 64KB。仅影响日志，不影响返回给客户端的响应体。
     */
    public static final int DEFAULT_MAX_LOG_BODY_SIZE = 64 * 1024;

    /**
     * @deprecated 日志上限已改为可配置，请使用 {@link #DEFAULT_MAX_LOG_BODY_SIZE} 或 {@link #getMaxLogBodySize()}。
     */
    @Deprecated
    public static final int MAX_LOG_BODY_SIZE = DEFAULT_MAX_LOG_BODY_SIZE;

    /**
     * 日志缓冲初始容量 8KB，覆盖 90%+ 的普通请求
     */
    private static final int INITIAL_BUFFER_SIZE = 8 * 1024;

    /**
     * 日志上限，由 filter 按配置传入
     */
    private final int maxLogBodySize;

    /**
     * 日志缓冲：initial 8KB，按需翻倍，上限 maxLogBodySize
     */
    private byte[] captured;

    /**
     * 日志缓冲中有效字节数
     */
    private int capturedLength = 0;

    /**
     * 是否因超出日志上限而丢弃了数据
     */
    private boolean truncated = false;

    /**
     * 实际写入客户端的字节数（可能远大于日志缓冲）
     */
    private long totalBytesWritten = 0;

    /**
     * 是否值得捕获日志；null 表示还没根据响应头判断
     */
    private Boolean captureEnabled;

    /**
     * 跳过捕获的原因（二进制、附件、压缩、SSE 等）
     */
    private String captureSkipReason;

    /**
     * sendError 的状态码；-1 表示未调用
     */
    private int errorStatus = -1;

    /**
     * sendError 的消息，可为 null
     */
    private String errorMessage;

    /**
     * 真正写给客户端的 outputStream（懒创建）
     */
    private ServletOutputStream outputStream;

    /**
     * 业务使用的 writer（懒创建，保持单实例）
     */
    private PrintWriter writer;

    public ResponseWrapper(HttpServletResponse response) {
        this(response, DEFAULT_MAX_LOG_BODY_SIZE);
    }

    /**
     * @param response       原始响应
     * @param maxLogBodySize 日志最多保留的字节数，仅影响日志，不影响客户端
     */
    public ResponseWrapper(HttpServletResponse response, int maxLogBodySize) {
        super(response);
        this.maxLogBodySize = maxLogBodySize > 0 ? maxLogBodySize : DEFAULT_MAX_LOG_BODY_SIZE;
        this.captured = new byte[Math.min(INITIAL_BUFFER_SIZE, this.maxLogBodySize)];
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("getWriter() has already been called on this response.");
        }
        if (outputStream == null) {
            outputStream = new CapturingServletOutputStream(super.getOutputStream());
        }
        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer != null) {
            return writer;
        }
        if (outputStream != null) {
            throw new IllegalStateException("getOutputStream() has already been called on this response.");
        }
        writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), resolveCharset()));
        return writer;
    }

    /**
     * flush 业务 writer 中的字符。filter 在请求结束（含异常/异步完成路径）时必须调用一次。
     */
    public void flushWriter() {
        if (writer != null) {
            writer.flush();
        }
    }

    @Override
    public void flushBuffer() throws IOException {
        flushWriter();
        super.flushBuffer();
    }

    @Override
    public void resetBuffer() {
        super.resetBuffer();
        clearCaptured();
    }

    @Override
    public void reset() {
        super.reset();
        clearCaptured();
    }

    /**
     * sendError 会清空响应缓冲；这里同步清空日志缓冲，并保留状态/消息供日志使用。
     * 容器随后渲染的错误页正文由容器/日志框架负责，不在此 wrapper 的捕获范围内。
     */
    @Override
    public void sendError(int sc) throws IOException {
        recordError(sc, null);
        super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
        recordError(sc, msg);
        super.sendError(sc, msg);
    }

    private void recordError(int sc, String msg) {
        clearCaptured();
        this.errorStatus = sc;
        this.errorMessage = msg;
    }

    /**
     * @return 是否调用过 sendError
     */
    public boolean isErrorSent() {
        return errorStatus >= 0;
    }

    /**
     * @return sendError 摘要，例如 {@code [sendError 404: not found]}；未调用时返回空串
     */
    public String getErrorSummary() {
        if (errorStatus < 0) {
            return "";
        }
        StringBuilder summary = new StringBuilder("[sendError ").append(errorStatus);
        if (errorMessage != null && !errorMessage.isEmpty()) {
            summary.append(": ").append(errorMessage);
        }
        return summary.append(']').toString();
    }

    private void clearCaptured() {
        capturedLength = 0;
        truncated = false;
        totalBytesWritten = 0;
        captureEnabled = null;
        captureSkipReason = null;
        errorStatus = -1;
        errorMessage = null;
    }

    /**
     * 日志缓冲直接引用（零拷贝），有效长度为 {@link #getCapturedLength()}。
     *
     * @return 日志缓冲数组
     */
    public byte[] getCapturedBuffer() {
        return captured;
    }

    /**
     * @return 日志缓冲中有效字节数
     */
    public int getCapturedLength() {
        return capturedLength;
    }

    /**
     * @return 日志上限（字节）
     */
    public int getMaxLogBodySize() {
        return maxLogBodySize;
    }

    /**
     * @return 是否因超出日志上限未进入日志缓冲
     */
    public boolean isTruncated() {
        return truncated;
    }

    /**
     * @return 是否根据响应头主动跳过了日志捕获（二进制/附件/压缩/SSE）
     */
    public boolean isCaptureSkipped() {
        return captureSkipReason != null;
    }

    /**
     * @return 跳过捕获的原因；未跳过时为 null
     */
    public String getCaptureSkipReason() {
        return captureSkipReason;
    }

    /**
     * @return 实际写入客户端的字节数
     */
    public long getTotalBytesWritten() {
        return totalBytesWritten;
    }

    /**
     * @deprecated 语义已变为“日志缓冲”，请使用 {@link #getCapturedBuffer()}。
     */
    @Deprecated
    public byte[] getBuffer() {
        return captured;
    }

    /**
     * @deprecated 语义已变为“日志缓冲有效长度”，请使用 {@link #getCapturedLength()}。
     */
    @Deprecated
    public int getWritePos() {
        return capturedLength;
    }

    /**
     * @deprecated 请使用 {@link #isTruncated()}。
     */
    @Deprecated
    public boolean isOverflow() {
        return truncated;
    }

    /**
     * 首次写入时判断是否需要捕获。响应头一般在写入前已由消息转换器设置。
     */
    private boolean shouldCapture() {
        if (captureEnabled == null) {
            String skipReason = resolveSkipReason();
            captureEnabled = skipReason == null;
            captureSkipReason = skipReason;
            if (skipReason != null) {
                LOGGER.debug("skip response body log: {}", skipReason);
            }
        }
        return captureEnabled;
    }

    private String resolveSkipReason() {
        String contentType = getContentType();
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.startsWith("text/event-stream")) {
                // SSE 可能是长连接/无界流，只记录字节数与耗时
                return "content-type=" + contentType;
            }
            if (isBinaryContentType(ct)) {
                return "content-type=" + contentType;
            }
        }
        String disposition = getHeader("Content-Disposition");
        if (disposition != null && disposition.toLowerCase(Locale.ROOT).contains("attachment")) {
            return "content-disposition=attachment";
        }
        String contentEncoding = getHeader("Content-Encoding");
        if (contentEncoding != null && !contentEncoding.isEmpty()
                && !"identity".equalsIgnoreCase(contentEncoding.trim())) {
            return "content-encoding=" + contentEncoding;
        }
        return null;
    }

    private static boolean isBinaryContentType(String contentType) {
        return contentType.startsWith("image/")
                || contentType.startsWith("audio/")
                || contentType.startsWith("video/")
                || contentType.startsWith("font/")
                || contentType.startsWith("application/octet-stream")
                || contentType.startsWith("application/pdf")
                || contentType.startsWith("application/zip")
                || contentType.startsWith("application/gzip")
                || contentType.startsWith("application/x-gzip")
                || contentType.startsWith("application/x-rar")
                || contentType.startsWith("application/x-7z")
                || contentType.startsWith("application/wasm")
                || contentType.startsWith("application/x-protobuf")
                || contentType.startsWith("application/protobuf")
                || contentType.startsWith("application/msgpack")
                || contentType.startsWith("application/cbor");
    }

    private void capture(byte[] bytes, int offset, int length) {
        int remaining = maxLogBodySize - capturedLength;
        if (remaining <= 0) {
            if (length > 0) {
                truncated = true;
            }
            return;
        }
        int copyLength = Math.min(length, remaining);
        ensureCapacity(capturedLength + copyLength);
        System.arraycopy(bytes, offset, captured, capturedLength, copyLength);
        capturedLength += copyLength;
        if (copyLength < length) {
            truncated = true;
        }
    }

    private void capture(int b) {
        if (capturedLength >= maxLogBodySize) {
            truncated = true;
            return;
        }
        ensureCapacity(capturedLength + 1);
        captured[capturedLength++] = (byte) b;
    }

    private void ensureCapacity(int required) {
        if (required <= captured.length) {
            return;
        }
        int newCapacity = captured.length;
        while (newCapacity < required) {
            newCapacity = Math.min(newCapacity * 2, maxLogBodySize);
        }
        captured = Arrays.copyOf(captured, newCapacity);
    }

    private Charset resolveCharset() {
        String encoding = getCharacterEncoding();
        if (encoding == null || encoding.isEmpty()) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName(encoding);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * 透传给真实 outputStream，同时旁路复制日志。
     */
    private final class CapturingServletOutputStream extends ServletOutputStream {

        private final ServletOutputStream delegate;

        private CapturingServletOutputStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            delegate.setWriteListener(listener);
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            totalBytesWritten++;
            if (shouldCapture()) {
                capture(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            totalBytesWritten += len;
            if (shouldCapture()) {
                capture(b, off, len);
            }
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
