package com.skycong.httprawlog.appender;

import com.skycong.httprawlog.api.History;
import com.skycong.httprawlog.api.HistoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

/**
 * 文件日志监听：把应用日志按 txId 关联到对应的请求历史。
 *
 * <p>1.1.0 起改为 Spring bean：状态实例化、调度线程为 daemon、由容器负责停止，
 * 不再使用静态字段和无法关闭的线程池。
 *
 * @author ruanmingcong
 * @since 2025/5/29 16:26
 */
public class FileAppender implements DisposableBean {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileAppender.class);

    /**
     * 日志缓存最大数据量
     */
    private static final int LOG_CACHES_LIMIT = 10000;

    private final HistoryStore historyStore;
    private final String filePath;
    private final ScheduledExecutorService executorService;

    /**
     * 日志临时内存缓存，最新的日志在队头。由 this 保护。
     */
    private final ArrayDeque<String> logCaches = new ArrayDeque<>();

    private long lastPosition = 0;

    private volatile boolean isRunning = false;

    /**
     * txId 解析函数
     */
    private volatile BiFunction<History, String, String> txIdFunction;

    /**
     * 日志 trace 匹配函数
     */
    private volatile BiFunction<String, String, Boolean> traceFunction = (txId, line) -> line.contains(txId);

    public FileAppender(HistoryStore historyStore, String filePath, int interval) {
        this.historyStore = historyStore == null ? HistoryStore.disabled() : historyStore;
        this.filePath = filePath;
        this.executorService = Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());
        this.isRunning = true;
        int safeInterval = Math.max(1, interval);
        // 定期检查文件更新
        this.executorService.scheduleAtFixedRate(this::readNewContent, 0, safeInterval, TimeUnit.SECONDS);
        LOGGER.debug("HistoryLog 监听服务启动, file = {}, interval = {}s.", filePath, safeInterval);
    }

    /**
     * 重写txId解析函数
     *
     * @param txIdFunction history=请求Log，string=当前行log，string=解析后的txId
     */
    public void setTxIdFunction(BiFunction<History, String, String> txIdFunction) {
        this.txIdFunction = txIdFunction;
    }

    /**
     * traceLog 判断
     *
     * @param traceFunction String=txId，string=当前行log，Boolean=true 当前行log包含txId，否则不包含
     */
    public void setTraceFunction(BiFunction<String, String, Boolean> traceFunction) {
        this.traceFunction = traceFunction;
    }

    /**
     * 停止读取文件（幂等）
     */
    public void stop() {
        if (!isRunning) {
            return;
        }
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

    @Override
    public void destroy() {
        stop();
    }

    /**
     * 读取文件新增内容
     */
    private void readNewContent() {
        if (!isRunning) {
            return;
        }
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                LOGGER.debug("监听的文件不存在{}", filePath);
                return;
            }
            long fileSize = file.length();

            // 首次读取文件大小（从文件末尾开始，不回放历史日志）
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
                    // 每次读取批次只取一次尚未关联 txId 的历史，避免每行都全量扫描
                    List<History> pendingHistories = pendingHistories();
                    String line;
                    while ((line = randomAccessFile.readLine()) != null) {
                        line = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                        addLogLine(line);
                        if (txIdFunction == null || pendingHistories.isEmpty()) {
                            continue;
                        }
                        Iterator<History> iterator = pendingHistories.iterator();
                        while (iterator.hasNext()) {
                            History history = iterator.next();
                            // 尝试解析txid并赋值
                            String txId = txIdFunction.apply(history, line);
                            if (txId != null) {
                                history.setTxId(txId);
                                attachTraceLogs(history, txId);
                                iterator.remove();
                                break;
                            }
                        }
                    }
                    // 更新最后读取位置
                    lastPosition = randomAccessFile.getFilePointer();
                }
            }
        } catch (IOException e) {
            LOGGER.error("读取文件时出错: {}", e.getMessage());
        } catch (Throwable t) {
            // 任何异常都不能让定时任务终止
            LOGGER.warn("读取文件时发生未预期异常: {}", t.toString());
        }
    }

    private List<History> pendingHistories() {
        List<History> pending = new ArrayList<>();
        for (History history : historyStore.snapshot()) {
            if (history.getTxId() == null) {
                pending.add(history);
            }
        }
        return pending;
    }

    private void addLogLine(String line) {
        synchronized (logCaches) {
            logCaches.addFirst(line);
            while (logCaches.size() > LOG_CACHES_LIMIT) {
                logCaches.removeLast();
            }
        }
    }

    private void attachTraceLogs(History history, String txId) {
        synchronized (logCaches) {
            Iterator<String> iterator = logCaches.iterator();
            while (iterator.hasNext()) {
                String next = iterator.next();
                if (traceFunction.apply(txId, next)) {
                    // 匹配上了，添加并移除缓存
                    history.appendLog(next);
                    iterator.remove();
                }
            }
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "http-raw-log-file-appender-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

}
