package com.skycong.httprawlog2;

import com.skycong.httprawlog.api.History;
import com.skycong.httprawlog.appender.FileAppender;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

import java.util.function.BiFunction;

@SpringBootApplication
public class HttpRawLogApplication {

    public static void main(String[] args) {
        SpringApplication.run(HttpRawLogApplication.class, args);
        System.out.println("http://localhost:8943/test/get/1");
    }


    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> shallowEtagHeaderFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new ShallowEtagHeaderFilter());
        bean.setOrder(Integer.MIN_VALUE);
        bean.addUrlPatterns("/*");
        return bean;
    }

}
