package com.test;

import com.skycong.httprawlog.api.HistoryApi;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HistoryApi 截断语义测试：存储层按 UTF-8 字节数截断，控制台按字符数截断。
 */
public class HistoryApiTruncateTest {

    /**
     * 按字节截断时不能把中文切成乱码。
     */
    @Test
    void truncate_isUtf8Safe() {
        String text = "中文中文";
        String truncated = HistoryApi.truncate(text, 4);

        assertTrue(truncated.startsWith("中"), "should keep one complete char, actual=" + truncated);
        assertFalse(truncated.contains("\uFFFD"), "must not contain replacement char");
        assertTrue(truncated.contains("maxBodyBytes=4"));
    }

    @Test
    void truncate_smallTextUnchanged() {
        assertEquals("abc", HistoryApi.truncate("abc", 4));
        assertEquals("abc", HistoryApi.truncate("abc", 0));
        assertEquals(null, HistoryApi.truncate(null, 10));
    }

    /**
     * 纯 ASCII 超限时直接按字符截断，不分配 byte[]，结果仍带总大小标记。
     */
    @Test
    void truncate_asciiOverLimit() {
        String truncated = HistoryApi.truncate("abcdef", 3);

        assertTrue(truncated.startsWith("abc"), "actual=" + truncated);
        assertTrue(truncated.contains("total size = 6"));
    }

    /**
     * 3 倍 UTF-16 长度仍在上限内时可以直接返回原文，避免编码。
     */
    @Test
    void truncate_multibyteWithinSafeUpperBound() {
        assertEquals("中文", HistoryApi.truncate("中文", 6));
        assertEquals("😀", HistoryApi.truncate("😀", 6));
    }

    /**
     * 混合大文本按 code point 扫描时，前缀不能超过字节上限，后缀要给出真实总字节数。
     */
    @Test
    void truncate_mixedLargeTextKeepsByteBoundary() {
        String text = "a".repeat(100) + "中文";
        String truncated = HistoryApi.truncate(text, 100);

        assertTrue(truncated.startsWith("a".repeat(100)), "actual prefix is not 100 ascii bytes");
        assertFalse(truncated.startsWith("a".repeat(101)));
        assertTrue(truncated.contains("total size = 106"));
    }

    /**
     * 4 字节 emoji 不能被切成半个 surrogate pair。
     */
    @Test
    void truncate_emojiKeepsWholeCodePoint() {
        String truncated = HistoryApi.truncate("😀😀", 5);

        assertTrue(truncated.startsWith("😀"), "actual=" + truncated);
        assertTrue(truncated.contains("total size = 8"));
    }

    /**
     * 前缀和总字节数必须与 JDK UTF-8 编码器一致。
     */
    @Test
    void truncate_matchesJdkUtf8Encoding() {
        String[] samples = {
                "a".repeat(100) + "中文",
                "abc中文😀xyz",
                "é".repeat(20),
                "中".repeat(20),
                "😀".repeat(10),
                "aé中😀".repeat(10)
        };
        int[] limits = {1, 2, 3, 4, 5, 6, 7, 8, 10, 16, 31, 32, 63, 100};
        for (String sample : samples) {
            for (int limit : limits) {
                assertEquals(reference(sample, limit), HistoryApi.truncate(sample, limit),
                        "sample length=" + sample.length() + ", limit=" + limit);
            }
        }
    }

    private static String reference(String text, int maxBodyBytes) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBodyBytes) {
            return text;
        }
        int end = maxBodyBytes;
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8)
                + "\n... [truncated, total size = " + bytes.length
                + " bytes > maxBodyBytes=" + maxBodyBytes + "]";
    }

    /**
     * maxChars 很小时也要截断，而不是返回全文。
     */
    @Test
    void preComputedTruncate_smallLimitStillTruncates() {
        assertEquals("ab", HistoryApi.preComputedTruncate("abcdef", 2));
        assertEquals("abc", HistoryApi.preComputedTruncate("abcdef", 3));
        assertEquals("ab...", HistoryApi.preComputedTruncate("abcdef", 5));
        assertEquals("abcdef", HistoryApi.preComputedTruncate("abcdef", 6));
        assertEquals("abcdef", HistoryApi.preComputedTruncate("abcdef", 0));
    }
}
