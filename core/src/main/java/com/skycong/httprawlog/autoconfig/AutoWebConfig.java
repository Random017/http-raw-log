package com.skycong.httprawlog.autoconfig;

import com.skycong.httprawlog.Constant;
import com.skycong.httprawlog.api.HistoryApi;
import com.skycong.httprawlog.api.HistoryRecord;
import com.skycong.httprawlog.api.HistoryStore;
import com.skycong.httprawlog.api.StatisticsApi;
import com.skycong.httprawlog.appender.FileAppender;
import com.skycong.httprawlog.filter.HttpRawLogFilter;
import jakarta.servlet.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 自动装配 HttpRawLogFilter ，预处理配置参数
 *
 * @author ruanmingcong 2020/4/5 11:27
 */
@AutoConfiguration
@Conditional(HttpRawLogEnabledCondition.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({Filter.class, FilterRegistrationBean.class, HttpRawLogFilter.class})
public class AutoWebConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoWebConfig.class);

    /**
     * 打印全局的http raw log 拦截器。
     *
     * <p>支持 {@code com.skycong.http-raw.log.open=false} 关闭；也兼容旧开关
     * {@code com.skycong.http-raw.log=false}（见 {@link HttpRawLogEnabledCondition}）。
     *
     * @param environment  environment
     * @param historyRecord 默认记录器（可能被使用方覆盖，允许为空）
     * @param statisticsApi 统计实现（统计关闭时为空）
     * @return filter bean
     */
    @Bean
    @ConditionalOnMissingBean(name = "httpRawLogFilterRegistration")
    public FilterRegistrationBean<HttpRawLogFilter> httpRawLogFilterRegistration(
            Environment environment,
            @Autowired(required = false) HistoryRecord historyRecord,
            @Autowired(required = false) StatisticsApi statisticsApi) {
        /*
         * 需要拦截处理的URL
         */
        String urlPatterns = environment.getProperty(Constant.LOG_URLS);
        urlPatterns = isEmpty(urlPatterns) ? Constant.PATH_ALL : urlPatterns;
        String[] split = urlPatterns.split(Constant.SPLIT);
        List<String> collect = Arrays.stream(split).filter(f -> !f.trim().isEmpty()).collect(Collectors.toList());
        if (collect.isEmpty()) {
            collect.add(Constant.PATH_ALL);
        }

        /*
         * 需要排除的URL
         */
        String urlExcludePatterns = environment.getProperty(Constant.LOG_URL_EXCLUDE);
        urlExcludePatterns = isEmpty(urlExcludePatterns) ? Constant.EMPTY : urlExcludePatterns;

        /*
         * HttpRawLogFilter 打印的请求头字段
         */
        String headers = environment.getProperty(Constant.LOG_HEADERS);
        headers = isEmpty(headers) ? Constant.CONTENT_TYPE : headers;

        // form-data 是否需要重新编码
        String formDataEncode = environment.getProperty(Constant.LOG_FORM_DATA_ENCODE);
        formDataEncode = isEmpty(formDataEncode) ? "0" : formDataEncode;

        // 是否统计接口耗时
        boolean logStatistics = booleanProperty(environment, Constant.LOG_STATISTICS, true);
        // 过滤器顺序
        int filterOrder = intProperty(environment, Constant.FILTER_ORDER, -100);
        // 请求体/响应日志上限
        int maxRequestBodyBytes = positiveIntProperty(environment, Constant.MAX_REQUEST_BODY_BYTES, 64 * 1024);
        int maxResponseLogBytes = positiveIntProperty(environment, Constant.MAX_RESPONSE_LOG_BYTES, 64 * 1024);

        FilterRegistrationBean<HttpRawLogFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new HttpRawLogFilter(historyRecord, statisticsApi));
        bean.setOrder(filterOrder);
        bean.addUrlPatterns(collect.toArray(new String[0]));
        bean.setName(Constant.FILTER_NAME);
        Map<String, String> map = new HashMap<>();
        map.put("logHeaders", headers);
        map.put("urlExcludePatterns", urlExcludePatterns);
        map.put("formDataEncodeFlag", formDataEncode);
        map.put("logStatistics", Boolean.toString(logStatistics));
        map.put("maxRequestBodyBytes", Integer.toString(maxRequestBodyBytes));
        map.put("maxResponseLogBytes", Integer.toString(maxResponseLogBytes));
        bean.setInitParameters(map);
        return bean;
    }

    /**
     * 历史记录存储，容量由 {@code com.skycong.http-raw.log.history} 控制，默认 0（不存储）。
     */
    @Bean
    @ConditionalOnMissingBean(HistoryStore.class)
    public HistoryStore historyStore(Environment environment) {
        int maxHistory = intProperty(environment, Constant.HISTORY_NUM, 0);
        long maxTotalBytes = longProperty(environment, Constant.HISTORY_MAX_TOTAL_BYTES, 4 * 1024 * 1024L);
        return new HistoryStore(maxHistory, maxTotalBytes);
    }

    /**
     * 历史请求记录 Controller，默认开启。
     *
     * <p>使用方注册自己的 {@link HistoryRecord} 后，该 Controller 不再创建。
     */
    @Bean
    @ConditionalOnMissingBean(HistoryRecord.class)
    public HistoryApi historyApi(Environment environment, HistoryStore historyStore) {
        int maxChars = intProperty(environment, Constant.HISTORY_MAX_CHARS, 203);
        int maxBodyBytes = positiveIntProperty(environment, Constant.HISTORY_MAX_BODY_BYTES, 32 * 1024);
        boolean consoleColors = booleanProperty(environment, Constant.CONSOLE_COLORS, System.console() != null);
        return new HistoryApi(historyStore, maxChars, maxBodyBytes, consoleColors);
    }

    /**
     * 统计 Controller，默认开启；{@code com.skycong.http-raw.log.statistics=false} 可关闭。
     */
    @Bean
    @ConditionalOnMissingBean(StatisticsApi.class)
    @ConditionalOnProperty(prefix = "com.skycong.http-raw.log", name = "statistics", havingValue = "true", matchIfMissing = true)
    public StatisticsApi statisticsApi() {
        return new StatisticsApi();
    }

    /**
     * 应用日志文件监听，仅在配置了 {@code appLogFilePath} 且 {@code history > 0} 时创建。
     */
    @Bean
    @ConditionalOnMissingBean(FileAppender.class)
    @Conditional(FileAppenderEnabledCondition.class)
    public FileAppender fileAppender(Environment environment, HistoryStore historyStore) {
        String appLogFilePath = environment.getProperty(Constant.APP_LOG_FILE_PATH);
        int tailInterval = intProperty(environment, Constant.TAIL_INTERVAL, 5);
        return new FileAppender(historyStore, appLogFilePath, tailInterval);
    }

    public static boolean isEmpty(String str) {
        return (str == null || str.isEmpty());
    }

    /**
     * 容错读取整型配置：配置写错时回退默认值并告警，而不是让宿主应用启动失败。
     */
    private static int intProperty(Environment environment, String key, int defaultValue) {
        String value = environment.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("invalid value for {} = {}, fallback to {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private static int positiveIntProperty(Environment environment, String key, int defaultValue) {
        int value = intProperty(environment, key, defaultValue);
        return value > 0 ? value : defaultValue;
    }

    private static long longProperty(Environment environment, String key, long defaultValue) {
        String value = environment.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("invalid value for {} = {}, fallback to {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private static boolean booleanProperty(Environment environment, String key, boolean defaultValue) {
        String value = environment.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        LOGGER.warn("invalid value for {} = {}, fallback to {}", key, value, defaultValue);
        return defaultValue;
    }
}
