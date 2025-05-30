package com.skycong.httprawlog2.config;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skycong.httprawlog.api.History;
import com.skycong.httprawlog.appender.FileAppender;
import com.yomahub.tlog.context.TLogContext;
import com.yomahub.tlog.web.common.TLogWebCommon;
import com.yomahub.tlog.web.interceptor.TLogWebInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TimeZone;
import java.util.function.BiFunction;

/**
 * @author ruanmingcong
 * @since 23/07/05 11:16
 */
@Configuration
public class WebConfig extends WebMvcConfigurationSupport {

    static {
        FileAppender.setTxIdFunction(new BiFunction<History, String, String>() {
            @Override
            public String apply(History history, String s) {
                boolean contains = s.contains(history.gethLogId());
                if (!contains) return null;
                String string = StrUtil.subBetween(s, "traceId=", ", hLogId=");
                return string;
            }
        });

    }


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Tlog
        registry.addInterceptor(tLogWebInterceptor);
    }

    @Override
    protected void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        super.extendMessageConverters(converters);
        // 针对get 请求，直接返回普通的字符串
        converters.removeIf(StringHttpMessageConverter.class::isInstance);
        converters.add(new StringHttpMessageConverter(StandardCharsets.UTF_8));

        converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
        ObjectMapper build = Jackson2ObjectMapperBuilder.json()
                .timeZone(TimeZone.getTimeZone("GMT+8"))
                .simpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .build();
        converters.add(new MappingJackson2HttpMessageConverter(build));
    }


    @Override
    protected void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        super.configureAsyncSupport(configurer);
        ThreadPoolTaskExecutor threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        threadPoolTaskExecutor.setMaxPoolSize(2);
        threadPoolTaskExecutor.initialize();

        configurer.setTaskExecutor(threadPoolTaskExecutor);
        configurer.setDefaultTimeout(30000);
    }


    private final TLogWebInterceptor tLogWebInterceptor = new TLogWebInterceptor() {
        @Override
        public boolean preHandleByHandlerMethod(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
            TLogWebCommon.loadInstance().preHandle(request);
            // 自定义header 名称
            response.addHeader("traceId", TLogContext.getTraceId());
            return true;
        }
    };
}
