package com.skycong.logrecord.config;

import com.skycong.logrecord.core.LogRecordAspect;
import com.skycong.logrecord.service.LogRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * @author ruanmingcong (005163) on 2022/4/22 16:31
 */
@Configuration
@Lazy
public class LogRecordConfiguration {


    /**
     * 配置 {com.skycong.logrecord.open} ，默认开启
     *
     * @param logRecordService {@link LogRecordService}
     * @return bean LogRecordAspect
     */
    @Bean
    @ConditionalOnExpression("${com.skycong.logrecord.open:true}")
    public LogRecordAspect logRecordAspect(@Autowired(required = false) LogRecordService logRecordService) {
        // 如果没有实现 LogRecordService ，使用默认实现
        if (logRecordService == null) {
            logRecordService = LogRecordService.DEFAULT_LOG_RECORD_SERVICE;
        }
        return new LogRecordAspect(logRecordService);
    }

}
