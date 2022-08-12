package com.skycong.httprawlog.autoconfig;

import com.skycong.httprawlog.Constant;
import com.skycong.httprawlog.filter.HttpRawLogFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

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
@Configuration
@Lazy
public class AutoWebConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoWebConfig.class);

    /**
     * ${com.skycong.http-raw.log} 该数据配置为true 开启，默认开启
     * 打印全局的http raw log 拦截器
     *
     * @param applicationContext application
     * @return filter bean
     */
    @Bean
    @ConditionalOnExpression("${com.skycong.http-raw.log:true}")
    public FilterRegistrationBean<HttpRawLogFilter> filterRegistrationBean(@Autowired ApplicationContext applicationContext) {
        /*
         * HttpRawLogFilter 拦截的urls 正则
         * 需要拦截处理的URL
         */
        String urlPatterns = applicationContext.getEnvironment().getProperty(Constant.LOG_URLS);
        urlPatterns = isEmpty(urlPatterns) ? Constant.STRING4 : urlPatterns;
        // 排除url 的后缀
        String urlExcludeSuffix = applicationContext.getEnvironment().getProperty(Constant.LOG_URL_EXCLUDE_SUFFIX);
        urlExcludeSuffix = isEmpty(urlExcludeSuffix) ? Constant.JS_CSS_HTML : urlExcludeSuffix;
        /*
         * HttpRawLogFilter 打印的请求头字段
         */
        String headers = applicationContext.getEnvironment().getProperty(Constant.LOG_HEADERS);
        headers = isEmpty(headers) ? Constant.CONTENT_TYPE : headers;
        // query string 是否需要重新编码
        String queryStringEncode = applicationContext.getEnvironment().getProperty(Constant.LOG_QUERY_STRING_ENCODE);
        queryStringEncode = isEmpty(queryStringEncode) ? Boolean.TRUE.toString() : queryStringEncode;

        String[] split = urlPatterns.split(Constant.SPLIT);
        List<String> collect = Arrays.stream(split).filter(f -> !f.trim().isEmpty()).collect(Collectors.toList());
        String[] strings1 = new String[collect.size()];
        collect.toArray(strings1);

        String[] split2 = headers.split(Constant.SPLIT);
        List<String> collect2 = Arrays.stream(split2).filter(f -> !f.trim().isEmpty()).collect(Collectors.toList());
        LOGGER.debug("init HttpRawLogFilter urls = {} exclude-suffix = {} ,log headers = {} ,queryStringEncode = {}", Arrays.toString(strings1), urlExcludeSuffix, collect2, queryStringEncode);
        FilterRegistrationBean<HttpRawLogFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new HttpRawLogFilter());
        bean.setOrder(Integer.MIN_VALUE);
        bean.addUrlPatterns(strings1);
        bean.setName("rawLogFilter");
        Map<String, String> map = new HashMap<>();
        map.put("logHeaders", headers);
        map.put("urlExcludeSuffix", urlExcludeSuffix);
        map.put("queryStringEncode", queryStringEncode);
        bean.setInitParameters(map);
        return bean;
    }

    public static boolean isEmpty(String str) {
        return (str == null || "".equals(str));
    }

}
