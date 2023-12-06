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
 * @author ruanmingcong (005163)
 * @since 23/11/30 17:57
 */
@RestController
public class StatisticsApi {

    /**
     * 统计即可
     *
     * @param type 统计类型，total：按照总请求次数统计，avg：按照平均耗时统计
     * @param sort desc 从大道小， asc 反之
     */
    @GetMapping("httpRawLog/statistics")
    public Object query(@RequestParam(value = "type", required = false, defaultValue = "total") String type,
                        @RequestParam(value = "sort", required = false, defaultValue = "desc") String sort) {
        if (type.equals("total")) {
            if (sort.equals("desc")) {
                return map.values().parallelStream().sorted(totalComparatorDesc).collect(Collectors.toList());
            } else {
                return map.values().parallelStream().sorted(totalComparatorAsc).collect(Collectors.toList());
            }
        } else {
            if (sort.equals("desc")) {
                return map.values().parallelStream().sorted(avgComparatorDesc).collect(Collectors.toList());
            } else {
                return map.values().parallelStream().sorted(avgComparatorAsc).collect(Collectors.toList());
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
        EXECUTORS.execute(() -> {
            StatisticsData data = map.get(requestURI);
            if (data == null) {
                data = new StatisticsData(requestURI);
                data.totalRequestNum++;
                data.averageTime = time;
                data.longestTime = time;
                data.shortestTime = time;
                map.put(requestURI, data);
            } else {
                // 整体在单线程里执行的，不存在并发问题
                data.totalRequestNum++;
                int t = data.averageTime; // 平均时间
                data.averageTime = t + (int) Math.round((time - t) / (data.totalRequestNum * 1.0D)); // 重新计算，四舍五入
                if (time > data.longestTime) {
                    data.longestTime = time;
                }
                if (time < data.shortestTime) {
                    data.shortestTime = time;
                }
            }
        });
    }

    /**
     * 统计数据对象
     */
    public static class StatisticsData {

        /**
         * uri
         */
        public String requestURI;
        /**
         * 累计请求次数
         */
        public int totalRequestNum = 0;
        /**
         * 接口平均耗时 单位：毫秒
         */
        public int averageTime = 0;
        /**
         * 最长耗时 单位：毫秒
         */
        public int longestTime = 0;
        /**
         * 最短耗时 单位：毫秒
         */
        public int shortestTime = 0;

        public StatisticsData() {
        }

        public StatisticsData(String requestURI) {
            this.requestURI = requestURI;
        }

    }


}
