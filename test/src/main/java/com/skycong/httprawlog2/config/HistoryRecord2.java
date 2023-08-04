package com.skycong.httprawlog2.config;

import com.skycong.httprawlog.api.History;
import com.skycong.httprawlog.api.HistoryRecord;
import org.springframework.stereotype.Component;

/**
 * 重写  HistoryRecord 后，默认记录最近的1000个请求历史记录接口将失效
 * @author ruanmingcong (005163)
 * @since 23/08/04 15:38
 */
// @Component
public class HistoryRecord2 implements HistoryRecord {
    @Override
    public void record(History history) {
        System.out.println(history);
    }
}
