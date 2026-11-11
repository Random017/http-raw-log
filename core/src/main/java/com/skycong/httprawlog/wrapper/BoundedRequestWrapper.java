package com.skycong.httprawlog.wrapper;

import com.skycong.httprawlog.util.CharsetSupport;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

/**
 * 未知 {@code Content-Length}（chunked）请求体的包装。
 *
 * <p>预读 {@code maxLogBytes + 1} 字节：
 * <ul>
 *   <li>未超限：完整缓存并回放，日志记录完整 body；</li>
 *   <li>超限：日志只保留前 {@code maxLogBytes} 字节，剩余部分通过
 *       {@link SequenceInputStream} 继续回放给业务，业务仍能读到完整 body。</li>
 * </ul>
 *
 * <p>预读缓冲从 8KB 开始按需扩容，不会因为配置了很大的 {@code maxLogBytes}
 * 而在每个请求上按上限预分配。无论是否截断，{@link #getInputStream()} 都返回
 * 同一个 {@link ServletInputStream} 实例，与容器原始流一样只能消费一次；
 * 不要在业务读取之后再期望重新从头读取。
 *
 * <p>该包装只用于非 form、非 multipart 的请求；form 请求需要完整 body 才能解析参数，
 * 仍走原有的“未知长度跳过”策略。
 *
 * @author ruanmingcong
 * @since 1.1.0
 */
public class BoundedRequestWrapper extends HttpServletRequestWrapper {

    /**
     * 预读缓冲初始大小；小 body 不会按 maxLogBytes 预分配
     */
    private static final int INITIAL_BUFFER_SIZE = 8 * 1024;

    /**
     * 日志保留的最大字节数
     */
    private final int maxLogBytes;

    /**
     * 已预读的 body：未截断时是完整 body；截断时是前 {@code maxLogBytes + 1} 字节
     */
    private final byte[] prefix;

    /**
     * 是否因超过日志上限而截断
     */
    private final boolean truncated;

    /**
     * 截断时：prefix 之后容器剩余输入流；未截断时为 null
     */
    private final InputStream remainingSource;

    /**
     * 统一回放的流实例；首次 getInputStream() 时创建
     */
    private ServletInputStream inputStream;

    /**
     * chunked 请求没有 form body，参数只来自 query string
     */
    private final Map<String, String[]> parameterMap;

    public BoundedRequestWrapper(HttpServletRequest request, int maxLogBytes) throws IOException {
        super(request);
        this.maxLogBytes = Math.max(1, Math.min(maxLogBytes, Integer.MAX_VALUE - 1));

        ServletInputStream input = request.getInputStream();
        byte[] buffer = new byte[Math.min(INITIAL_BUFFER_SIZE, this.maxLogBytes + 1)];
        int total = 0;
        while (total <= this.maxLogBytes) {
            if (total == buffer.length) {
                int grown = (int) Math.min((long) buffer.length * 2, (long) this.maxLogBytes + 1);
                buffer = Arrays.copyOf(buffer, grown);
            }
            int read = input.read(buffer, total, buffer.length - total);
            if (read < 0) {
                break;
            }
            if (read == 0) {
                // 阻塞式 ServletInputStream 正常情况下不会返回 0；兜底读一个字节避免空转。
                int single = input.read();
                if (single < 0) {
                    break;
                }
                buffer[total++] = (byte) single;
            } else {
                total += read;
            }
        }

        this.truncated = total > this.maxLogBytes;
        if (truncated) {
            this.prefix = buffer;
            this.remainingSource = input;
        } else {
            this.prefix = Arrays.copyOf(buffer, total);
            this.remainingSource = null;
        }
        this.parameterMap = RequestWrapper.getQueryStringMap(request.getQueryString());
    }

    /**
     * 返回同一个回放流；与容器原始流一致，只可消费一次。
     */
    @Override
    public synchronized ServletInputStream getInputStream() {
        if (inputStream == null) {
            InputStream source = truncated
                    ? new SequenceInputStream(new ByteArrayInputStream(prefix), remainingSource)
                    : new ByteArrayInputStream(prefix);
            inputStream = new ReplayServletInputStream(source);
        }
        return inputStream;
    }

    /**
     * 业务侧 reader 保持声明编码；只有日志字符串使用 {@link CharsetSupport#resolve} 的启发式。
     */
    @Override
    public java.io.BufferedReader getReader() throws IOException {
        return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(),
                CharsetSupport.resolveForReader(getContentType(), super.getCharacterEncoding())));
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return parameterMap;
    }

    @Override
    public String getParameter(String name) {
        String[] values = parameterMap.get(name);
        return values != null && values.length > 0 ? values[0] : null;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(parameterMap.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return parameterMap.get(name);
    }

    /**
     * 日志用的 body 字符串；截断时追加说明。
     */
    public String getRequestBodyLogString() {
        int logLength = Math.min(prefix.length, maxLogBytes);
        if (logLength == 0) {
            return "";
        }
        Charset charset = CharsetSupport.resolve(getContentType(), super.getCharacterEncoding(), prefix, logLength);
        String body = new String(prefix, 0, logLength, charset);
        if (truncated) {
            body += "\n... [request body truncated: unknown/chunked length, only first "
                    + maxLogBytes + " bytes logged]";
        }
        return body;
    }

    /**
     * @return 是否发生截断
     */
    public boolean isTruncated() {
        return truncated;
    }

    /**
     * 简单回放流；仅支持同步读取，setReadListener 为空实现（与 {@link RequestWrapper} 保持一致）。
     */
    private static final class ReplayServletInputStream extends ServletInputStream {

        private final InputStream delegate;
        private boolean finished;

        private ReplayServletInputStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            finished = value < 0;
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int value = delegate.read(b, off, len);
            if (value < 0) {
                finished = true;
            }
            return value;
        }

        @Override
        public long skip(long n) throws IOException {
            return delegate.skip(n);
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public boolean isFinished() {
            return finished;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
            // 同步回放不支持异步读取
        }
    }
}
