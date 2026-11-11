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

    private static History history(String id) {
        return new History("t", id, "GET", "/test", "", "", "", "200", "", "", 1);
    }

    private static History historyWithBody(String id, int bodyLength) {
        return new History("t", id, "GET", "/test", "", "", "", "200", "", "x".repeat(bodyLength), 1);
    }
}
