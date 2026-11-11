package com.test;

import com.skycong.httprawlog.api.HistoryApi;
import org.junit.jupiter.api.Test;

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
