package com.skycong.httprawlog.api;

import com.skycong.httprawlog.filter.HttpRawLogFilter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Collectors;

/**
 * @author ruanmingcong (005163)
 * @since 23/08/04 10:59
 */
@RestController
public class HistoryApi implements HistoryRecord {

    /**
     * 内存缓存
     */
    public static final ConcurrentLinkedDeque<History> HISTORIES = new ConcurrentLinkedDeque<>();

    @GetMapping("httpRawLog/history")
    List<History> history(@RequestParam(value = "hLogId", required = false) String hLogId,
                          @RequestParam(value = "page", required = false, defaultValue = "1") int page,
                          History history) {
        // 优先logId 检索
        if (hLogId != null && !hLogId.isEmpty()) {
            return HISTORIES.stream().filter(f -> f.gethLogId().equals(hLogId)).collect(Collectors.toList());
        }
        // 分页
        page = Math.max(page, 1);
        page = Math.min(page, 20);
        int skip = (page - 1) * 50, limit = 50;
        if (skip >= HISTORIES.size()) {
            skip = HISTORIES.size();
            limit = 0;
        }
        if ((skip + limit) >= HISTORIES.size()) {
            limit = HISTORIES.size() - skip;
        }
        return HISTORIES.stream().skip(skip).limit(limit).collect(Collectors.toList());
    }

    /**
     * 最大历史记录
     */
    private int maxHistory = 1000;

    public HistoryApi() {
    }

    public HistoryApi(int maxHistory) {
        this.maxHistory = maxHistory;
        HttpRawLogFilter.LOGGER.debug("init default History API complete maxHistory = {}.", maxHistory);
    }

    /**
     * 默认实现方法，打印到控制台，并记录到内存中
     */
    @Override
    public void record(History history) {
        consoleLog(history);
        // 添加到头, 时间最新的在前面
        HISTORIES.addFirst(history);
        if (HISTORIES.size() > maxHistory) {
            HISTORIES.removeLast();
            if (HISTORIES.size() > maxHistory) {
                HISTORIES.removeLast();
            }
        }
    }


    //log 打印到控制台上
    public static void consoleLog(History history) {
        StringBuilder sb = new StringBuilder();
        sb.append('\n')
                .append("--------------> http raw data ").append(logf1("hLogId:")).append(history.gethLogId()).append('\n')
                .append(logf2("[url]")).append(":").append(history.getMethod()).append('\t').append(history.getUrl()).append('\n')
                .append(logf2("[request headers]")).append(":").append(history.getRequestHeaders()).append('\n');
        if (!history.getQueryString().isEmpty()) {
            sb.append(logf2("[query string]")).append(":").append(history.getQueryString()).append('\n');
        }
        if (!history.getRequestBody().isEmpty()) {
            sb.append(logf2("[request body]")).append(":").append(history.getRequestBody()).append('\n');
        }
        sb.append(logf3("[response headers]")).append(":status=").append(history.getResponseStatus()).append(", ").append(history.getResponseHeaders()).append('\n');
        if (!history.getResponseBody().isEmpty()) {
            sb.append(logf3("[response body]")).append(":").append(history.getResponseBody());
        }
        HttpRawLogFilter.LOGGER.debug(sb.toString());
    }


    static String logf1(String log) {
        // 青色
        return "\u001B[36m" + log + "\u001B[0m";
    }

    static String logf2(String log) {
        // 绿色
        return "\u001B[32m" + log + "\u001B[0m";
    }

    static String logf3(String log) {
        // 蓝色
        return "\u001B[34m" + log + "\u001B[0m";
    }
}
