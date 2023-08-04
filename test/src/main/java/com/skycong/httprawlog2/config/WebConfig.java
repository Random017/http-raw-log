package com.skycong.httprawlog2.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.TimeZone;

/**
 * @author ruanmingcong (005163)
 * @since 23/07/05 11:16
 */
@Configuration
public class WebConfig extends WebMvcConfigurationSupport {

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
}
