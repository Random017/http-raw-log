package com.test;

import com.skycong.httprawlog.api.History;
import com.skycong.httprawlog.api.HistoryStore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HistoryStore 有界存储语义测试。
 */
public class HistoryStoreTest {

    @Test
    void keepsOnlyNewestEntries() {
        HistoryStore store = new HistoryStore(2);
        store.add(history("1"));
        store.add(history("2"));
        store.add(history("3"));

        assertEquals(2, store.size());
        assertEquals("3", store.snapshot().get(0).gethLogId());
        assertEquals("2", store.snapshot().get(1).gethLogId());
        assertTrue(store.findByLogId("1").isEmpty());
        assertEquals(1, store.findByLogId("3").size());
    }

    @Test
    void disabledStore_storesNothing() {
        HistoryStore store = new HistoryStore(0);
        assertFalse(store.isEnabled());
        store.add(history("1"));
        assertEquals(0, store.size());
        assertTrue(store.snapshot().isEmpty());
        assertTrue(store.page(1, 50).isEmpty());
    }

    @Test
    void page_outOfRangeReturnsEmpty() {
        HistoryStore store = new HistoryStore(5);
        store.add(history("1"));

        assertEquals(1, store.page(1, 50).size());
        assertTrue(store.page(2, 50).isEmpty());
        assertTrue(store.page(Integer.MAX_VALUE, 50).isEmpty(), "large page must not overflow back to page 1");
    }

    /**
     * 总字节数上限：即使条数没满，也要淘汰最旧记录，避免 100×4MB 占满堆。
     */
    @Test
    void maxTotalBytes_evictsOldestEvenWhenCountNotFull() {
        HistoryStore store = new HistoryStore(100, 100);
        store.add(historyWithBody("1", 60));
        store.add(historyWithBody("2", 60));
        store.add(historyWithBody("3", 60));

        assertEquals(1, store.size());
        assertEquals("3", store.snapshot().get(0).gethLogId());
        assertTrue(store.getTotalBytes() <= 100, "totalBytes should stay under cap, actual=" + store.getTotalBytes());
    }

    /**
     * 总字节数必须按 UTF-8 字节计算，否则 CJK 内容会被低估。
     */
    @Test
    void totalBytes_countsUtf8BytesNotCharacters() {
        HistoryStore store = new HistoryStore(10, 1000);
        store.add(new History(null, null, null, null, null, null, null, null, null, "中文", 0));
        assertEquals(6, store.getTotalBytes());
    }

    @Test
    void totalBytes_asciiFastPathMatchesCharacterCount() {
        HistoryStore store = new HistoryStore(10, 1000);
        store.add(new History(null, null, null, null, null, null, null, null, null, "abcdef", 0));
        assertEquals(6, store.getTotalBytes());
    }

    /**
     * 入库后追加链路日志也要计入预算；超限时淘汰最旧记录，不能出现负预算。
     */
    @Test
    void appendTraceLog_updatesBudgetAndEvictsOldest() {
        HistoryStore store = new HistoryStore(10, 50);
        History first = historyWithBody("1", 10);
        History second = historyWithBody("2", 10);
        store.add(first);
        store.add(second);

        store.appendTraceLog(first, "0123456789");

        assertTrue(store.getTotalBytes() <= 50, "totalBytes=" + store.getTotalBytes());
        assertEquals(1, store.size());
        assertFalse(store.snapshot().contains(first), "oldest history should be evicted");
        assertTrue(store.snapshot().contains(second));
    }

    /**
     * 链路日志按 UTF-8 字节计入预算，而不是字符数。
     */
    @Test
    void appendTraceLog_countsUtf8Bytes() {
        HistoryStore store = new HistoryStore(10, 1000);
        History history = history("1");
        store.add(history);
        long before = store.getTotalBytes();

        store.appendTraceLog(history, "中文");

        assertEquals(before + 6, store.getTotalBytes());
    }

    /**
     * 入库后设置 txId 也要计入预算，否则淘汰时会减掉从未计入的字节。
     */
    @Test
    void setTxId_updatesBudget() {
        HistoryStore store = new HistoryStore(10, 1000);
        History history = history("1");
        store.add(history);
        long before = store.getTotalBytes();

        store.setTxId(history, "tx-123");

        assertEquals(before + 6, store.getTotalBytes());
        assertEquals("tx-123", history.getTxId());
    }

    /**
     * 2 字节字符、4 字节 emoji、未配对 surrogate 都要按 UTF-8 实际字节数计入预算。
     */
    @Test
    void appendTraceLog_countsUtf8BytesForAllCharacterWidths() {
        HistoryStore store = new HistoryStore(10, 1000);
        History history = history("1");
        store.add(history);
        long before = store.getTotalBytes();

        store.appendTraceLog(history, "é");
        store.appendTraceLog(history, "😀");
        store.appendTraceLog(history, "\uD800");

        assertEquals(before + 2 + 4 + 1, store.getTotalBytes());
    }

    /**
     * 已被淘汰的 history 不再接收链路日志，避免在 store 外继续增长。
     */
    @Test
    void appendTraceLog_ignoresEvictedHistory() {
        HistoryStore store = new HistoryStore(1, 1000);
        History first = history("1");
        History second = history("2");
        store.add(first);
        store.add(second); // 条数上限淘汰 first

        long before = store.getTotalBytes();
        store.appendTraceLog(first, "ignored");

        assertEquals(before, store.getTotalBytes());
        assertFalse(first.getTraceLogs() != null && first.getTraceLogs().contains("ignored"));
    }

    /**
     * 单条 history 的链路日志必须有上限，避免长连接无限累积。
     */
    @Test
    void traceLogs_areCappedPerHistory() {
        History history = history("1");
        for (int i = 0; i < History.MAX_TRACE_LOGS + 10; i++) {
            history.appendLog("line-" + i);
        }

        assertEquals(History.MAX_TRACE_LOGS, history.getTraceLogs().size());
    }

    private static History history(String id) {
        return new History("t", id, "GET", "/test", "", "", "", "200", "", "", 1);
    }

    private static History historyWithBody(String id, int bodyLength) {
        return new History("t", id, "GET", "/test", "", "", "", "200", "", "x".repeat(bodyLength), 1);
    }
}
