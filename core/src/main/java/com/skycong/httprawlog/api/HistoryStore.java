package com.skycong.httprawlog.api;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;

/**
 * 有界的历史请求存储，替代原先的 {@code HistoryApi.HISTORIES} 静态集合。
 *
 * <p>由 Spring 容器创建为单例 bean，容量在构造时固定：
 * <ul>
 *   <li>{@code maxHistory > 0}：最多保留 maxHistory 条，最新的在最前面；</li>
 *   <li>{@code maxHistory <= 0}：不存储历史（只保留控制台输出）；</li>
 *   <li>{@code maxTotalBytes > 0}：按条数和总字节数双淘汰，避免 100 条 × 4MB 占满堆。</li>
 * </ul>
 *
 * <p>线程安全，无后台线程，不需要生命周期回调。
 *
 * @author ruanmingcong
 * @since 1.1.0
 */
public class HistoryStore {

    /**
     * 禁用状态下的空实现，避免调用方到处判空
     */
    private static final HistoryStore DISABLED = new HistoryStore(0);

    private final LinkedBlockingDeque<History> histories;

    private final int maxHistory;

    private final long maxTotalBytes;

    /**
     * 当前存储内容的近似 UTF-8 字节数，由 {@link #add(History)} / {@link #clear()} 维护
     */
    private long totalBytes = 0;

    public HistoryStore(int maxHistory) {
        this(maxHistory, Long.MAX_VALUE);
    }

    public HistoryStore(int maxHistory, long maxTotalBytes) {
        this.maxHistory = Math.max(0, maxHistory);
        this.maxTotalBytes = maxTotalBytes > 0 ? maxTotalBytes : Long.MAX_VALUE;
        this.histories = this.maxHistory > 0 ? new LinkedBlockingDeque<>(this.maxHistory) : null;
    }

    public static HistoryStore disabled() {
        return DISABLED;
    }

    public boolean isEnabled() {
        return histories != null;
    }

    public int getMaxHistory() {
        return maxHistory;
    }

    public long getMaxTotalBytes() {
        return maxTotalBytes;
    }

    /**
     * @return 当前存储内容的近似 UTF-8 字节数
     */
    public synchronized long getTotalBytes() {
        return totalBytes;
    }

    public int size() {
        return histories == null ? 0 : histories.size();
    }

    /**
     * 记录一条历史，超出条数或字节上限时丢弃最旧的一条。
     */
    public synchronized void add(History history) {
        if (history == null || histories == null) {
            return;
        }
        long historySize = sizeOf(history);
        // 单条记录就超过总预算：直接拒绝，避免把整个 store 挤空后仍超限
        if (historySize > maxTotalBytes) {
            return;
        }
        // 先按总字节数淘汰，直到当前条能放进去（至少保留一条）
        while (!histories.isEmpty() && totalBytes + historySize > maxTotalBytes) {
            History removed = histories.pollLast();
            if (removed == null) {
                break;
            }
            totalBytes -= sizeOf(removed);
        }
        // 再按条数淘汰
        while (!histories.offerFirst(history)) {
            History removed = histories.pollLast();
            if (removed == null) {
                break;
            }
            totalBytes -= sizeOf(removed);
        }
        totalBytes += historySize;
    }

    public List<History> findByLogId(String hLogId) {
        if (histories == null || hLogId == null) {
            return new ArrayList<>();
        }
        return histories.stream()
                .filter(history -> hLogId.equals(history.gethLogId()))
                .collect(Collectors.toList());
    }

    /**
     * 分页读取，page 从 1 开始，返回快照列表。
     */
    public List<History> page(int page, int pageSize) {
        if (histories == null || pageSize <= 0) {
            return new ArrayList<>();
        }
        int size = histories.size();
        int skip = Math.min(Math.max(0, (page - 1) * pageSize), size);
        int limit = Math.min(pageSize, size - skip);
        if (limit <= 0) {
            return new ArrayList<>();
        }
        return histories.stream().skip(skip).limit(limit).collect(Collectors.toList());
    }

    /**
     * 当前内容快照，供 appender 等只读场景使用。
     */
    public List<History> snapshot() {
        return histories == null ? new ArrayList<>() : new ArrayList<>(histories);
    }

    public synchronized void clear() {
        if (histories != null) {
            histories.clear();
            totalBytes = 0;
        }
    }

    /**
     * 近似大小：按 UTF-8 字节数统计主要字符串字段，用于总量控制，不要求精确。
     */
    static long sizeOf(History history) {
        if (history == null) {
            return 0;
        }
        long size = length(history.gethLogId())
                + length(history.getMethod())
                + length(history.getUrl())
                + length(history.getRequestHeaders())
                + length(history.getQueryString())
                + length(history.getRequestBody())
                + length(history.getResponseStatus())
                + length(history.getResponseHeaders())
                + length(history.getResponseBody())
                + length(history.getTxId());
        List<String> traceLogs = history.getTraceLogs();
        if (traceLogs != null) {
            for (String log : traceLogs) {
                size += length(log);
            }
        }
        return size;
    }

    private static int length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
