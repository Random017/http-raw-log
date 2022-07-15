package com.skycong.redisweb.config;

import com.skycong.redisweb.controller.RedisWebController;
import com.skycong.redisweb.service.RedisWebService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author ruanmingcong (005163)
 * @since 2022/5/24 11:09
 */
@Configuration
@ConditionalOnClass(RedisTemplate.class)
public class RedisWebAutoConfig implements WebMvcConfigurer {

    ///////////////////////////////////////////////////////////////////////////
    // 常量定义
    ///////////////////////////////////////////////////////////////////////////

    public static final String REDIS_WEB_ADMIN_TEMPLATE = "redisWebAdminTemplate";
    public static final Charset UTF8 = StandardCharsets.UTF_8;


    /**
     * 通过web 客户端和服务端通信，数据传输都是string 类型（UTF_8）
     */
    @Bean(REDIS_WEB_ADMIN_TEMPLATE)
    public RedisTemplate<String, String> redisWebAdminTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();
        StringRedisSerializer serializer = StringRedisSerializer.UTF_8;
        redisTemplate.setStringSerializer(serializer);

        redisTemplate.setKeySerializer(serializer);
        redisTemplate.setValueSerializer(serializer);

        redisTemplate.setHashKeySerializer(serializer);
        redisTemplate.setHashValueSerializer(serializer);

        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }


    @Bean
    @ConditionalOnBean(name = REDIS_WEB_ADMIN_TEMPLATE)
    public RedisWebService redisWebService(@Autowired @Qualifier(REDIS_WEB_ADMIN_TEMPLATE) RedisTemplate<String, String> redisTemplate) {
        return new RedisWebService(redisTemplate);
    }


    @Bean
    @ConditionalOnBean(RedisWebService.class)
    @ConditionalOnMissingBean(RedisWebController.class)
    public RedisWebController redisWebController(@Autowired RedisWebService redisWebService) {
        return new RedisWebController(redisWebService);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/redisWebAdmin/**")
                .allowedHeaders("*")
                .allowedHeaders("*")
                .allowedOrigins("*")
                .allowCredentials(true);
        WebMvcConfigurer.super.addCorsMappings(registry);
    }
}
