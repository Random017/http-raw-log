package com.skycong.httprawlog.api;

/**
 * 记录历史日志 默认实现{@link HistoryApi} 里面打印日志到控制台调用了该方法 {@link HistoryApi#consoleLog}
 *
 * @author ruanmingcong
 * @since 23/08/04 11:16
 */
public interface HistoryRecord {

    /**
     * 记录，如需要打印到控制台上{@link HistoryApi#consoleLog}
     */
    void record(History history);
}
