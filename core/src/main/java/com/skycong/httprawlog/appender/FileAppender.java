package com.skycong.httprawlog.appender;

import com.skycong.httprawlog.api.History;
import com.skycong.httprawlog.api.HistoryApi;
import com.skycong.httprawlog.filter.HttpRawLogFilter;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * 文件日志监听
 *
 * @author ruanmingcong
 * @since 2025/5/29 16:26
 */
public class FileAppender {

    // 日志临时内存缓存
    private static final ConcurrentLinkedDeque<String> LOG_CACHES = new ConcurrentLinkedDeque<>();
    // 日志缓存最大数据量
    private static final int LOG_CACHES_LIMIT = 10000;

    // 日志文件路径
    private static String filePath;
    private static long lastPosition = 0;
    // 定时任务监听日志文件
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private static boolean isRunning = false;

    /**
     * txId 解析函数
     */
    private static BiFunction<History, String, String> txIdFunction;
    /**
     * 日志tr
     */
    private static BiFunction<String, String, Boolean> traceFunction = (txId, line) -> line.contains(txId);

    /**
     * 重写txId解析函数
     *
     * @param txIdFunction history=请求Log，string=当前行log，string=解析后的txId
     */
    public static void setTxIdFunction(BiFunction<History, String, String> txIdFunction) {
        FileAppender.txIdFunction = txIdFunction;
    }

    /**
     * traceLog 判断
     *
     * @param traceFunction String=txId，string=当前行log，Boolean=true 当前行log包含txId，否则不包含
     */
    public static void setTraceFunction(BiFunction<String, String, Boolean> traceFunction) {
        FileAppender.traceFunction = traceFunction;
    }

    /**
     * 开始持续读取文件
     *
     * @param interval 检查文件更新的时间间隔(秒)
     */
    public static void start(String filePath, int interval) {
        FileAppender.filePath = filePath;
        if (isRunning) {
            HttpRawLogFilter.LOGGER.warn("HistoryLog 监听服务已启动.");
            return;
        }
        isRunning = true;
        // 定期检查文件更新
        executorService.scheduleAtFixedRate(FileAppender::readNewContent, 0, interval, TimeUnit.SECONDS);
        HttpRawLogFilter.LOGGER.debug("HistoryLog 监听服务启动.");
    }

    /**
     * 停止读取文件
     */
    public void stop() {
        isRunning = false;
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 读取文件新增内容
     */
    private static void readNewContent() {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                HttpRawLogFilter.LOGGER.warn("监听的文件不存在{}", filePath);
                return;
            }
            long fileSize = file.length();

            // 首次读取文件大小
            if (lastPosition == 0) {
                lastPosition = fileSize;
            }

            // 如果文件变小，说明文件被截断或重写，从头开始读取
            if (fileSize < lastPosition) {
                lastPosition = 0;
            }


            // 如果文件有新内容
            if (fileSize > lastPosition) {
                try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
                    // 移动到上次读取的位置
                    randomAccessFile.seek(lastPosition);
                    String line;
                    while ((line = randomAccessFile.readLine()) != null) {
                        // 从head 开收遍历
                        // 将日志添加到内存缓存，最新的日志在队列头
                        line = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                        LOG_CACHES.addFirst(line);
                        if (LOG_CACHES.size() > LOG_CACHES_LIMIT) {
                            LOG_CACHES.removeLast();
                            if (LOG_CACHES.size() > LOG_CACHES_LIMIT) {
                                LOG_CACHES.removeLast();
                            }
                        }
                        // 过滤为空的txid 记录
                        List<History> collect = HistoryApi.HISTORIES.stream().filter(f -> f.getTxId() == null).collect(Collectors.toList());
                        for (History history : collect) {
                            // 尝试解析txid并赋值
                            String txId = txIdFunction.apply(history, LOG_CACHES.getFirst());
                            if (txId != null) {
                                history.setTxId(txId);
                                // List<History> collect1 = HistoryApi.HISTORIES.stream().filter(f -> f.getTxId() != null).collect(Collectors.toList());
                                // 遍历日志缓存
                                Iterator<String> iterator = LOG_CACHES.iterator();
                                while (iterator.hasNext()) {
                                    String next = iterator.next();
                                    if (traceFunction.apply(txId, next)) {
                                        // 匹配上了，添加并移除缓存
                                        history.appendLog(next);
                                        iterator.remove();
                                    }
                                }
                                break;
                            }
                        }
                    }
                    // 更新最后读取位置
                    lastPosition = randomAccessFile.getFilePointer();
                }
            }
        } catch (IOException e) {
            HttpRawLogFilter.LOGGER.error("读取文件时出错: {}", e.getMessage());
        }
    }

}
