package com.skycong.logrecord.config;

import com.skycong.logrecord.core.LogRecordAspect;
import com.skycong.logrecord.pojo.LogRecordPojo;
import com.skycong.logrecord.service.OperatorService;
import com.skycong.logrecord.service.RecordLogService;
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
     */
    @Bean
    @ConditionalOnExpression("${com.skycong.logrecord.open:true}")
    public LogRecordAspect logRecordAspect(@Autowired(required = false) OperatorService operatorService,
                                           @Autowired(required = false) RecordLogService recordLogService) {
        // 如果没有实现 operatorService ，使用默认实现
        if (operatorService == null) {
            operatorService = new OperatorService() {
                @Override
                public String getCurrentOperator(String operator) {
                    return OperatorService.super.getCurrentOperator(operator);
                }
            };
        }
        // 如果没有 recordLogService 的实现，使用默认实现
        if (recordLogService == null) {
            recordLogService = new RecordLogService() {
                @Override
                public void record(LogRecordPojo logRecordPojo) {
                    if (LogRecordAspect.LOGGER.isDebugEnabled()) {
                        LogRecordAspect.LOGGER.debug(logRecordPojo.toString());
                    }
                }
            };
        }
        return new LogRecordAspect(operatorService, recordLogService);
    }

}
