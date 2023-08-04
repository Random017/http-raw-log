package com.skycong.httprawlog.api;

/**
 * 记录历史日志
 *
 * @author ruanmingcong (005163)
 * @since 23/08/04 11:16
 */
public interface HistoryRecord {

    /**
     * 记录
     */
    void record(History history);
}
