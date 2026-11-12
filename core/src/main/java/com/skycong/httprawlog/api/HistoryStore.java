package com.skycong.httprawlog.api;

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
     * 当前存储内容的近似 UTF-8 字节数，由 {@link #add(History)}、{@link #appendTraceLog(History, String)}、
     * {@link #setTxId(History, String)} 和 {@link #clear()} 维护
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

    /**
     * 向仍在 store 中的 history 追加一条链路日志，并同步维护字节预算。
     *
     * <p>历史记录加入 store 之后，{@link History#appendLog(String)} 产生的日志也必须计入
     * {@code maxTotalBytes}，否则淘汰时会把从未计入的字节减掉，导致预算失真。
     * 已被淘汰的 history 不再接收链路日志，避免在 store 外继续增长。
     *
     * @param history 目标 history
     * @param log     日志行
     */
    public synchronized void appendTraceLog(History history, String log) {
        if (history == null || log == null || histories == null || !histories.contains(history)) {
            return;
        }
        int before = history.getTraceLogs() == null ? 0 : history.getTraceLogs().size();
        history.appendLog(log);
        int after = history.getTraceLogs() == null ? 0 : history.getTraceLogs().size();
        if (after == before) {
            // 已达到单条日志行数上限，预算不变
            return;
        }
        totalBytes += length(log);
        trimToBudget();
    }

    /**
     * 给仍在 store 中的 history 设置 txId，并同步维护字节预算。
     *
     * <p>{@code FileAppender} 在请求结束后才解析 txId；如果直接调用
     * {@link History#setTxId(String)}，淘汰时会把未计入预算的 txId 字节减掉。
     *
     * @param history 目标 history
     * @param txId    链路 ID，可为 null
     */
    public synchronized void setTxId(History history, String txId) {
        if (history == null || histories == null) {
            return;
        }
        boolean tracked = histories.contains(history);
        int before = length(history.getTxId());
        history.setTxId(txId);
        if (!tracked) {
            return;
        }
        totalBytes += length(txId) - before;
        trimToBudget();
    }

    /**
     * 按总字节预算淘汰最旧记录；调用方必须持有本对象锁。
     */
    private void trimToBudget() {
        while (totalBytes > maxTotalBytes && !histories.isEmpty()) {
            History removed = histories.pollLast();
            if (removed == null) {
                break;
            }
            totalBytes -= sizeOf(removed);
        }
        if (totalBytes < 0) {
            // 外部直接改过 History 时可能出现账面负数；按当前内容重算一次，避免预算永久失真
            long recalculated = 0;
            for (History history : histories) {
                recalculated += sizeOf(history);
            }
            totalBytes = recalculated;
        }
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
        // 用 long 计算偏移，避免 page/pageSize 很大时 int 乘法溢出后又从首页开始
        long requestedSkip = Math.max(0L, ((long) page - 1L) * pageSize);
        int skip = (int) Math.min(requestedSkip, size);
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
        if (value == null || value.isEmpty()) {
            return 0;
        }
        // 纯 ASCII 的字符数就是 UTF-8 字节数，避免为常见 JSON/日志分配临时 byte[]
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7F) {
                return utf8Length(value);
            }
        }
        return value.length();
    }

    /**
     * 统计 UTF-8 字节数；未配对的 surrogate 与 JDK 编码器一致按 1 字节（替换字符）计。
     */
    private static int utf8Length(String value) {
        int bytes = 0;
        for (int i = 0; i < value.length(); ) {
            int codePoint = value.codePointAt(i);
            if (codePoint <= 0x7F) {
                bytes += 1;
            } else if (codePoint <= 0x7FF) {
                bytes += 2;
            } else if (codePoint >= 0xD800 && codePoint <= 0xDFFF) {
                bytes += 1;
            } else if (codePoint <= 0xFFFF) {
                bytes += 3;
            } else {
                bytes += 4;
            }
            i += Character.charCount(codePoint);
        }
        return bytes;
    }
}
