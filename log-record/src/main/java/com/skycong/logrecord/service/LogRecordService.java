package com.skycong.logrecord.service;

import com.skycong.logrecord.core.LogRecord;
import com.skycong.logrecord.core.LogRecordAspect;
import com.skycong.logrecord.pojo.LogRecordPojo;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;

/**
 * 自定义处理逻辑
 *
 * @author ruanmingcong 2022.4.22 下午 10:30
 */
public interface LogRecordService {

    /**
     * 获取当前的操作人信息
     *
     * <p>
     * 通常在web服务中，当前操作人一般从 userContext 中获取
     * </p>
     *
     * @param operator {@link LogRecord#operator()} 使用SpEl解析后的值
     * @return 默认实现，返回itself
     */
    default String getCurrentOperator(String operator) {
        return operator;
    }

    /**
     * 添加自定义函数到 {@link LogRecordAspect.LogRecordEvaluationContext} 中，以便SpEl 可调用,(建议返回的map使用缓存，以便减少方法同步调用影响)
     *
     * <p>
     * 参考文档 <a href="https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions-ref-functions">functions</a>
     * </p>
     *
     * @return map[SpEl名称，方法对象]
     */
    default Map<String, Method> getFunctions() {
        return Collections.emptyMap();
    }

    /**
     * 记录日志 ，（建议实现方法异步处理日志，减少对业务逻辑的阻塞影响）
     * <p>
     * 默认实现 log.debug(logRecordPojo) ,需要 {@link com.skycong.logrecord.core.LogRecordAspect} logging.level 设置为debug
     * </p>
     *
     * @param logRecordPojo {@link LogRecordPojo}
     */
    default void record(LogRecordPojo logRecordPojo) {
        if (LogRecordAspect.LOGGER.isDebugEnabled()) {
            LogRecordAspect.LOGGER.debug(logRecordPojo.toString());
        }
    }

    /**
     * 默认实现
     */
    LogRecordService DEFAULT_LOG_RECORD_SERVICE = new LogRecordService() {
        @Override
        public String getCurrentOperator(String operator) {
            return LogRecordService.super.getCurrentOperator(operator);
        }

        @Override
        public Map<String, Method> getFunctions() {
            return LogRecordService.super.getFunctions();
        }

        @Override
        public void record(LogRecordPojo logRecordPojo) {
            LogRecordService.super.record(logRecordPojo);
        }
    };

}
