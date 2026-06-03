package com.skycong.httprawlog.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 统计每个请求的平均耗时，和累计次数
 *
 * @author ruanmingcong
 * @since 23/11/30 17:57
 */
@RestController
public class StatisticsApi {

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

    // 统计计算的单线程，长度2000，超过的请求直接丢弃
    private final static ExecutorService EXECUTORS = new ThreadPoolExecutor(
            1, 1, 30, TimeUnit.MINUTES,
            new LinkedBlockingDeque<>(2000), new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    // 统计的数据内存缓存map
    private final static ConcurrentHashMap<String, StatisticsData> map = new ConcurrentHashMap<>();

    /**
     * 统计请求Url耗时
     *
     * @param requestURI 请求Url
     * @param time       耗时，（单位：毫秒）
     */
    public static void statistics(String requestURI, int time) {
        EXECUTORS.execute(() -> map.compute(requestURI, (key, data) -> {
            if (data == null) {
                data = new StatisticsData(requestURI);
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
