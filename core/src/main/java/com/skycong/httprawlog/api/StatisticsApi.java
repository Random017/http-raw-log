package com.skycong.httprawlog.api;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 统计每个请求的平均耗时，和累计次数
 *
 * <p>状态与线程池都是实例级的，由 Spring 容器创建和销毁；线程为 daemon，
 * 不会阻止宿主应用退出。
 *
 * @author ruanmingcong
 * @since 23/11/30 17:57
 */
@RestController
public class StatisticsApi implements DisposableBean {

    /**
     * 统计即可
     *
     * @param type 统计类型，total：按照总请求次数统计，avg：按照平均耗时统计
     * @param sort desc 从大到小， asc 反之
     */
    @GetMapping("httpRawLog/statistics")
    public Object query(@RequestParam(value = "type", required = false, defaultValue = "total") String type,
                        @RequestParam(value = "sort", required = false, defaultValue = "desc") String sort) {
        if (type.equals("total")) {
            if (sort.equals("desc")) {
                return map.values().stream().sorted(totalComparatorDesc).collect(Collectors.toList());
            } else {
                return map.values().stream().sorted(totalComparatorAsc).collect(Collectors.toList());
            }
        } else {
            if (sort.equals("desc")) {
                return map.values().stream().sorted(avgComparatorDesc).collect(Collectors.toList());
            } else {
                return map.values().stream().sorted(avgComparatorAsc).collect(Collectors.toList());
            }
        }
    }


    // 四个排序器
    private static final Comparator<StatisticsData> totalComparatorDesc = Comparator.comparingInt(o -> -1 * o.totalRequestNum);
    private static final Comparator<StatisticsData> totalComparatorAsc = Comparator.comparingInt(o -> o.totalRequestNum);
    private static final Comparator<StatisticsData> avgComparatorDesc = Comparator.comparingInt(o -> -1 * o.averageTime);
    private static final Comparator<StatisticsData> avgComparatorAsc = Comparator.comparingInt(o -> o.averageTime);

    // 统计计算的单线程，长度2000，超过的请求直接丢弃；daemon 线程，避免拖住宿主进程退出
    private final ExecutorService executors;

    // 统计的数据内存缓存map
    private final ConcurrentHashMap<String, StatisticsData> map = new ConcurrentHashMap<>();

    /**
     * 允许统计的最大 URI 数量，防止路径参数/随机 URL 把 map 撑爆
     */
    private static final int MAX_URI_KEYS = 1000;

    private static final java.util.regex.Pattern UUID_SEGMENT = java.util.regex.Pattern.compile(
            "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)");
    private static final java.util.regex.Pattern NUMERIC_SEGMENT = java.util.regex.Pattern.compile(
            "/\\d+(?=/|$)");
    private static final java.util.regex.Pattern LONG_HEX_SEGMENT = java.util.regex.Pattern.compile(
            "/[0-9a-fA-F]{16,}(?=/|$)");

    public StatisticsApi() {
        this.executors = new ThreadPoolExecutor(
                1, 1, 30, TimeUnit.MINUTES,
                new LinkedBlockingDeque<>(2000),
                new StatisticsThreadFactory(),
                new ThreadPoolExecutor.DiscardOldestPolicy()
        );
    }

    /**
     * 统计请求Url耗时
     *
     * @param requestURI 请求Url
     * @param time       耗时，（单位：毫秒）
     */
    public void record(String requestURI, int time) {
        if (requestURI == null || requestURI.isEmpty()) {
            return;
        }
        String uri = normalizeUri(requestURI);
        // 新增 URI 达到上限后丢弃，避免无界路径参数撑爆统计 map
        if (map.size() >= MAX_URI_KEYS && !map.containsKey(uri)) {
            return;
        }
        try {
            executors.execute(() -> map.compute(uri, (key, data) -> {
                if (data == null) {
                    data = new StatisticsData(uri);
                    data.totalRequestNum = 1;
                    data.averageTime = time;
                    data.longestTime = time;
                    data.shortestTime = time;
                } else {
                    data.totalRequestNum++;
                    int t = data.averageTime;
                    data.averageTime = t + (int) Math.round((time - t) / (data.totalRequestNum * 1.0D));
                    if (time > data.longestTime) {
                        data.longestTime = time;
                    }
                    if (time < data.shortestTime) {
                        data.shortestTime = time;
                    }
                }
                return data;
            }));
        } catch (RejectedExecutionException e) {
            // 容器关闭或队列满：统计数据可以丢，不能影响业务
        }
    }

    /**
     * 把数字/UUID/长十六进制路径参数归一化为 {id}，避免 /order/1、/order/2 ... 无限增长。
     *
     * @param requestURI 原始请求 URI
     * @return 归一化后的 URI
     */
    public static String normalizeUri(String requestURI) {
        if (requestURI == null || requestURI.isEmpty()) {
            return "/";
        }
        String uri = requestURI;
        int queryIndex = uri.indexOf('?');
        if (queryIndex >= 0) {
            uri = uri.substring(0, queryIndex);
        }
        int fragmentIndex = uri.indexOf('#');
        if (fragmentIndex >= 0) {
            uri = uri.substring(0, fragmentIndex);
        }
        uri = UUID_SEGMENT.matcher(uri).replaceAll("/{id}");
        uri = LONG_HEX_SEGMENT.matcher(uri).replaceAll("/{id}");
        uri = NUMERIC_SEGMENT.matcher(uri).replaceAll("/{id}");
        return uri;
    }

    @Override
    public void destroy() {
        executors.shutdownNow();
    }

    private static final class StatisticsThreadFactory implements ThreadFactory {

        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "http-raw-log-statistics-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * 统计数据对象
     */
    public static class StatisticsData {

        /**
         * uri
         */
        public volatile String requestURI;
        /**
         * 累计请求次数
         */
        public volatile int totalRequestNum = 0;
        /**
         * 接口平均耗时 单位：毫秒
         */
        public volatile int averageTime = 0;
        /**
         * 最长耗时 单位：毫秒
         */
        public volatile int longestTime = 0;
        /**
         * 最短耗时 单位：毫秒
         */
        public volatile int shortestTime = 0;

        public StatisticsData() {
        }

        public StatisticsData(String requestURI) {
            this.requestURI = requestURI;
        }

    }


}
