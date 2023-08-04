package com.skycong.httprawlog.autoconfig;

import com.skycong.httprawlog.Constant;
import com.skycong.httprawlog.api.HistoryApi;
import com.skycong.httprawlog.api.HistoryRecord;
import com.skycong.httprawlog.filter.HttpRawLogFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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

    /**
     * ${com.skycong.http-raw.log} 该数据配置为true 开启，默认开启
     * 打印全局的http raw log 拦截器
     *
     * @param applicationContext application
     * @return filter bean
     */
    @Bean
    @ConditionalOnExpression("${com.skycong.http-raw.log:true}")
    public FilterRegistrationBean<HttpRawLogFilter> filterRegistrationBean(@Autowired ApplicationContext applicationContext,
                                                                           @Autowired(required = false) HistoryRecord historyRecord) {

        /*
         * HttpRawLogFilter 拦截的urls 正则
         * 需要拦截处理的URL
         */
        String urlPatterns = applicationContext.getEnvironment().getProperty(Constant.LOG_URLS);
        urlPatterns = isEmpty(urlPatterns) ? Constant.STRING4 : urlPatterns;
        String[] split = urlPatterns.split(Constant.SPLIT);
        List<String> collect = Arrays.stream(split).filter(f -> !f.trim().isEmpty()).collect(Collectors.toList());
        String[] strings1 = new String[collect.size()];
        collect.toArray(strings1);
        /*
         * 需要排除的URL
         */
        String urlExcludePatterns = applicationContext.getEnvironment().getProperty(Constant.LOG_URL_EXCLUDE);
        urlExcludePatterns = isEmpty(urlExcludePatterns) ? Constant.EMPTY : urlExcludePatterns;

        // 排除url 的后缀
        String urlExcludeSuffix = applicationContext.getEnvironment().getProperty(Constant.LOG_URL_EXCLUDE_SUFFIX);
        urlExcludeSuffix = isEmpty(urlExcludeSuffix) ? Constant.JS_CSS_HTML : urlExcludeSuffix;
        /*
         * HttpRawLogFilter 打印的请求头字段
         */
        String headers = applicationContext.getEnvironment().getProperty(Constant.LOG_HEADERS);
        headers = isEmpty(headers) ? Constant.CONTENT_TYPE : headers;
        // form-data 是否需要重新编码
        String formDataEncode = applicationContext.getEnvironment().getProperty(Constant.LOG_FORM_DATA_ENCODE);
        formDataEncode = isEmpty(formDataEncode) ? "0" : formDataEncode;

        FilterRegistrationBean<HttpRawLogFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new HttpRawLogFilter(historyRecord));
        bean.setOrder(Integer.MIN_VALUE);
        bean.addUrlPatterns(strings1);
        bean.setName(Constant.FILTER_NAME);
        Map<String, String> map = new HashMap<>();
        map.put("logHeaders", headers);
        map.put("urlExcludePatterns", urlExcludePatterns);
        map.put("urlExcludeSuffix", urlExcludeSuffix);
        map.put("formDataEncodeFlag", formDataEncode);
        bean.setInitParameters(map);
        return bean;
    }


    @Bean
    @ConditionalOnMissingBean(HistoryRecord.class)
    @ConditionalOnExpression("${com.skycong.http-raw.log.history:1000} > 0")
    public HistoryApi historyApi(@Autowired ApplicationContext applicationContext) {
        return new HistoryApi(applicationContext.getEnvironment().getProperty("com.skycong.http-raw.log.history", Integer.class, 1000));
    }

    public static boolean isEmpty(String str) {
        return (str == null || "".equals(str));
    }

}
