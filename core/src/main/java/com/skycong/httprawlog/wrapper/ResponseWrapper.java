package com.skycong.httprawlog.wrapper;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 对 response 包装，将响应的 outputStream 缓存到固定 buffer 中，并重新写回返回给客户端。
 * 日志最多使用 {@link #MAX_LOG_BODY_SIZE} 字节，超出部分直接丢弃不存储，防止 OOM。
 * 业务侧收到的响应体受 buffer 上限截断（最多 4MB）。
 *
 * @author ruanmingcong
 * @since 2022/8/11 15:47
 */
public class ResponseWrapper extends HttpServletResponseWrapper {

    /**
     * 日志/缓冲区最大字节数，默认 4MB。
     */
    public static final int MAX_LOG_BODY_SIZE = 4 * 1024 * 1024;

    /**
     * buffer 初始容量 8KB，覆盖 90%+ 的普通请求
     */
    private static final int INITIAL_BUFFER_SIZE = 8 * 1024;

    /**
     * 缓冲区：初始 8KB，按需翻倍扩容，上限 4MB
     */
    private byte[] buffer = new byte[INITIAL_BUFFER_SIZE];

    /**
     * 当前写入位置
     */
    private int writePos = 0;

    /**
     * 是否因超出上限而丢弃了数据
     */
    private boolean overflow = false;

    public ResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() {
        return new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
            }

            @Override
            public void write(int b) {
                if (writePos >= MAX_LOG_BODY_SIZE) {
                    overflow = true;
                    return;
                }
                if (writePos == buffer.length) {
                    int newCap = Math.min(buffer.length * 2, MAX_LOG_BODY_SIZE);
                    buffer = Arrays.copyOf(buffer, newCap);
                }
                buffer[writePos++] = (byte) b;
            }
        };
    }

    @Override
    public PrintWriter getWriter() {
        // 适配 writer 路径：将字符写入同一个 buffer
        return new PrintWriter(new java.io.OutputStreamWriter(
                new java.io.OutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        getOutputStream().write(b);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        for (int i = off; i < off + len; i++) {
                            getOutputStream().write(b[i]);
                        }
                    }
                }, StandardCharsets.UTF_8));
    }

    /**
     * 获取缓冲区直接引用（零拷贝）
     *
     * @return 缓冲区数组
     */
    public byte[] getBuffer() {
        return buffer;
    }

    /**
     * 获取实际写入的字节数
     *
     * @return 写入字节数
     */
    public int getWritePos() {
        return writePos;
    }

    /**
     * 是否有数据因超出上限被丢弃
     *
     * @return true 有溢出
     */
    public boolean isOverflow() {
        return overflow;
    }
}
