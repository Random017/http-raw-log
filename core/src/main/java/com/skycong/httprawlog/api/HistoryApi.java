package com.skycong.httprawlog.api;

import com.skycong.httprawlog.filter.HttpRawLogFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author ruanmingcong
 * @since 23/08/04 10:59
 */
@RestController
public class HistoryApi implements HistoryRecord {

    /**
     * 写入 history 的单个 body/query 最大字节数（存储层限额）
     */
    private final int maxBodyBytes;

    /**
     * response body 控制台最大字符长度
     */
    private final int maxChars;

    /**
     * 内存存储（实例状态，由 Spring 管理生命周期）
     */
    private final HistoryStore historyStore;

    /**
     * 控制台是否输出 ANSI 颜色（重定向到文件/非 TTY 时可关闭）
     */
    private final boolean consoleColors;

    /**
     * 保留无参构造，便于测试/手工实例化；等价于“不存储历史”
     */
    public HistoryApi() {
        this(HistoryStore.disabled(), 203, 32 * 1024, true);
    }

    /**
     * 兼容旧构造方式：内部创建一个独立 store
     */
    public HistoryApi(int maxHistory, int maxChars) {
        this(new HistoryStore(maxHistory, 4 * 1024 * 1024L), maxChars, 32 * 1024, true);
    }

    public HistoryApi(HistoryStore historyStore, int maxChars) {
        this(historyStore, maxChars, 32 * 1024, true);
    }

    public HistoryApi(HistoryStore historyStore, int maxChars, int maxBodyBytes) {
        this(historyStore, maxChars, maxBodyBytes, true);
    }

    public HistoryApi(HistoryStore historyStore, int maxChars, int maxBodyBytes, boolean consoleColors) {
        this.historyStore = historyStore == null ? HistoryStore.disabled() : historyStore;
        this.maxChars = maxChars;
        this.maxBodyBytes = maxBodyBytes > 0 ? maxBodyBytes : 32 * 1024;
        this.consoleColors = consoleColors;
        HttpRawLogFilter.LOGGER.debug("init default History API complete maxHistory = {}, maxChars = {}, maxBodyBytes = {}, consoleColors = {}.",
                this.historyStore.getMaxHistory(), maxChars, this.maxBodyBytes, consoleColors);
    }

    /**
     * 历史接口
     *
     * @param hLogId 响应头里面hLogId
     * @param page   分页参数，默认 1
     */
    @GetMapping("httpRawLog/history")
    public List<History> history(@RequestParam(value = "hLogId", required = false) String hLogId,
                                 @RequestParam(value = "page", required = false, defaultValue = "1") int page) {
        // 优先logId 检索
        if (hLogId != null && !hLogId.isEmpty()) {
            return historyStore.findByLogId(hLogId);
        }
        // 分页：最多 20 页，每页 50 条
        page = Math.max(1, Math.min(page, 20));
        return historyStore.page(page, 50);
    }

    /**
     * 按 hLogId 取单条详情；列表和详情返回的 body 都已按 {@code maxBodyBytes} 截断。
     *
     * @param hLogId 响应头里的 hLogId
     * @return 单条历史；不存在返回 404
     */
    @GetMapping("httpRawLog/history/{hLogId}")
    public ResponseEntity<History> historyDetail(@PathVariable("hLogId") String hLogId) {
        List<History> histories = historyStore.findByLogId(hLogId);
        if (histories.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(histories.get(0));
    }

    /**
     * 默认实现方法，打印到控制台，并记录到内存中
     */
    @Override
    public void record(History history) {
        if (history == null) {
            return;
        }
        // 存储层单独限额：控制台与内存历史都不再保存超大 body
        history.setQueryString(truncate(history.getQueryString(), maxBodyBytes));
        history.setRequestBody(truncate(history.getRequestBody(), maxBodyBytes));
        history.setResponseBody(truncate(history.getResponseBody(), maxBodyBytes));

        // 先打印到控制台
        consoleLog(history);

        // 记录请求日志到内存中（容量/字节数用尽时由 store 丢弃最旧记录）
        historyStore.add(history);
    }


    //log 打印到控制台上
    public void consoleLog(History history) {
        if (!HttpRawLogFilter.LOGGER.isDebugEnabled()) {
            return;
        }
        // 预分配接近最终长度，避免大 body 反复扩容拷贝；上限 1MB，异常大时再自然增长
        int responseChars = maxChars > 0
                ? Math.min(lengthOf(history.getResponseBody()), maxChars)
                : lengthOf(history.getResponseBody());
        long estimate = 256L
                + lengthOf(history.getRequestHeaders())
                + lengthOf(history.getQueryString())
                + lengthOf(history.getRequestBody())
                + responseChars;
        StringBuilder sb = new StringBuilder((int) Math.min(estimate, 1L << 20));
        sb.append('\n')
                .append("--------------> http raw data ").append(color1("hLogId:")).append(history.gethLogId()).append("  ").append(history.getCost()).append(" ms").append('\n')
                .append(color2("[url]")).append(":").append(history.getMethod()).append('\t').append(history.getUrl()).append('\n')
                .append(color2("[request headers]")).append(":").append(history.getRequestHeaders()).append('\n');
        if (notEmpty(history.getQueryString())) {
            sb.append(color2("[query string]")).append(":").append(history.getQueryString()).append('\n');
        }
        if (notEmpty(history.getRequestBody())) {
            sb.append(color2("[request body]")).append(":").append(history.getRequestBody()).append('\n');
        }
        sb.append(color3("[response headers]")).append(":status=").append(history.getResponseStatus()).append(", ").append(history.getResponseHeaders()).append('\n');
        if (notEmpty(history.getResponseBody())) {
            sb.append(color3("[response body]")).append(":").append(preComputedTruncate(history.getResponseBody(), maxChars));
        }
        HttpRawLogFilter.LOGGER.debug(sb.toString());
    }

    private static boolean notEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    /**
     * 按 UTF-8 字节数截断，避免把多字节字符切成乱码。
     *
     * @param text         原文
     * @param maxBodyBytes 最大字节数；&lt;=0 表示不截断
     * @return 截断后的文本（带总大小标记）
     */
    public static String truncate(String text, int maxBodyBytes) {
        if (text == null || maxBodyBytes <= 0) {
            return text;
        }
        int textLength = text.length();
        // UTF-8 每个 UTF-16 code unit 最多 3 字节；满足时可跳过整段编码。
        if ((long) textLength * 3 <= maxBodyBytes) {
            return text;
        }
        // 纯 ASCII 与字节一一对应，超限时直接按字符截断，避免为大 body 分配 byte[]。
        if (isAscii(text)) {
            if (textLength <= maxBodyBytes) {
                return text;
            }
            return text.substring(0, maxBodyBytes) + truncateSuffix(textLength, maxBodyBytes);
        }

        // 非 ASCII 时按 code point 扫描，避免为超长 body 再分配一份完整 UTF-8 byte[]
        long totalBytes = 0;
        int prefixEnd = 0;
        boolean prefixComplete = true;
        for (int i = 0; i < textLength; ) {
            int codePoint = text.codePointAt(i);
            int chars = Character.charCount(codePoint);
            int bytes = utf8Bytes(codePoint);
            totalBytes += bytes;
            if (prefixComplete) {
                if (totalBytes <= maxBodyBytes) {
                    prefixEnd = i + chars;
                } else {
                    prefixComplete = false;
                }
            }
            i += chars;
        }
        if (prefixComplete) {
            return text;
        }
        return text.substring(0, prefixEnd) + truncateSuffix(totalBytes, maxBodyBytes);
    }

    /**
     * 与 JDK UTF-8 编码器一致的单 code point 字节数；未配对的 surrogate 会被替换成
     * 单字节 {@code ?}，因此按 1 字节计。
     */
    private static int utf8Bytes(int codePoint) {
        if (codePoint >= 0xD800 && codePoint <= 0xDFFF) {
            return 1;
        }
        if (codePoint < 0x80) {
            return 1;
        }
        if (codePoint < 0x800) {
            return 2;
        }
        if (codePoint < 0x10000) {
            return 3;
        }
        return 4;
    }

    private static boolean isAscii(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) > 0x7F) {
                return false;
            }
        }
        return true;
    }

    private static String truncateSuffix(long totalBytes, int maxBodyBytes) {
        return "\n... [truncated, total size = " + totalBytes
                + " bytes > maxBodyBytes=" + maxBodyBytes + "]";
    }

    /**
     * 预计算长度的截断（避免重复计算）。
     *
     * @param text     原文
     * @param maxChars 最大字符数；&lt;=0 表示不截断
     * @return 截断后的文本
     */
    public static String preComputedTruncate(String text, int maxChars) {
        if (text == null || maxChars <= 0) return text;

        final int textLength = text.length();
        if (textLength <= maxChars) {
            return text;
        }
        if (maxChars <= 3) {
            return text.substring(0, maxChars);
        }
        return text.substring(0, maxChars - 3) + "...";
    }

    // 彩色日志（可通过 com.skycong.http-raw.log.console.colors=false 关闭）
    private String color1(String log) {
        return consoleColors ? "\u001B[36m" + log + "\u001B[0m" : log;
    }

    private String color2(String log) {
        return consoleColors ? "\u001B[32m" + log + "\u001B[0m" : log;
    }

    private String color3(String log) {
        return consoleColors ? "\u001B[34m" + log + "\u001B[0m" : log;
    }
}
