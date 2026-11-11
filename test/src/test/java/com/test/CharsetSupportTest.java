package com.test;

import com.skycong.httprawlog.util.CharsetSupport;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 日志 body 字符集选择测试，覆盖无 charset 的 JSON 和 ISO-8859-1 回退。
 */
public class CharsetSupportTest {

    @Test
    void contentTypeCharsetWins() {
        Charset gbk = Charset.forName("GBK");
        byte[] bytes = "中文".getBytes(StandardCharsets.UTF_8);
        assertEquals(gbk, CharsetSupport.resolve("text/plain;charset=GBK", "ISO-8859-1", bytes, bytes.length));
    }

    @Test
    void quotedCharsetIsParsed() {
        Charset charset = CharsetSupport.charsetFromContentType("text/plain; charset=\"UTF-8\"");
        assertSame(StandardCharsets.UTF_8, charset);
    }

    @Test
    void jsonWithoutCharsetUsesUtf8() {
        byte[] bytes = "{\"name\":\"中文JSON响应\"}".getBytes(StandardCharsets.UTF_8);
        assertSame(StandardCharsets.UTF_8,
                CharsetSupport.resolve("application/json", "ISO-8859-1", bytes, bytes.length));
    }

    @Test
    void vendorJsonAndXmlWithoutCharsetUseUtf8() {
        byte[] bytes = "中文".getBytes(StandardCharsets.UTF_8);
        assertSame(StandardCharsets.UTF_8,
                CharsetSupport.resolve("application/vnd.api+json", "ISO-8859-1", bytes, bytes.length));
        assertSame(StandardCharsets.UTF_8,
                CharsetSupport.resolve("application/problem+xml", "ISO-8859-1", bytes, bytes.length));
    }

    @Test
    void explicitJsonCharsetIsRespected() {
        byte[] bytes = "{\"name\":\"中文\"}".getBytes(StandardCharsets.UTF_8);
        assertSame(StandardCharsets.ISO_8859_1,
                CharsetSupport.resolve("application/json;charset=ISO-8859-1", "UTF-8", bytes, bytes.length));
    }

    @Test
    void utf8BytesWithIsoDeclarationFallBackToUtf8() {
        byte[] bytes = "café".getBytes(StandardCharsets.UTF_8);
        assertSame(StandardCharsets.UTF_8,
                CharsetSupport.resolve("text/plain", "ISO-8859-1", bytes, bytes.length));
    }

    @Test
    void utf8BytesWithUsAsciiDeclarationFallBackToUtf8() {
        byte[] bytes = "中文".getBytes(StandardCharsets.UTF_8);
        assertSame(StandardCharsets.UTF_8,
                CharsetSupport.resolve("text/plain", "US-ASCII", bytes, bytes.length));
    }

    @Test
    void realLatin1BytesStayLatin1() {
        byte[] bytes = new byte[]{'c', 'a', 'f', (byte) 0xE9};
        assertSame(StandardCharsets.ISO_8859_1,
                CharsetSupport.resolve("text/plain", "ISO-8859-1", bytes, bytes.length));
    }

    @Test
    void truncatedUtf8IsStillDetected() {
        byte[] bytes = "中文".getBytes(StandardCharsets.UTF_8);
        // 只保留“中”和一个不完整的“文”，模拟日志上限截断
        byte[] truncated = new byte[4];
        System.arraycopy(bytes, 0, truncated, 0, truncated.length);
        assertSame(StandardCharsets.UTF_8,
                CharsetSupport.resolve("text/plain", "ISO-8859-1", truncated, truncated.length));
    }

    @Test
    void invalidCharsetFallsBackSafely() {
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        Charset charset = CharsetSupport.resolve("text/plain;charset=not-a-charset", "ISO-8859-1", bytes, bytes.length);
        assertTrue(StandardCharsets.ISO_8859_1.equals(charset) || StandardCharsets.UTF_8.equals(charset));
        assertFalse(charset == null);
    }

    /**
     * 业务 reader 不做 UTF-8 启发式：显式 charset 优先，其次 JSON/XML 默认 UTF-8，
     * 最后才用容器声明编码；未声明时按 Servlet 规范 ISO-8859-1。
     */
    @Test
    void readerCharsetKeepsDeclaredSemantics() {
        assertSame(Charset.forName("GBK"),
                CharsetSupport.resolveForReader("text/plain;charset=GBK", "UTF-8"));
        assertSame(StandardCharsets.UTF_8,
                CharsetSupport.resolveForReader("application/json", "ISO-8859-1"));
        assertSame(StandardCharsets.ISO_8859_1,
                CharsetSupport.resolveForReader("text/plain", "ISO-8859-1"));
        assertSame(StandardCharsets.ISO_8859_1,
                CharsetSupport.resolveForReader("text/plain", null));
    }
}
