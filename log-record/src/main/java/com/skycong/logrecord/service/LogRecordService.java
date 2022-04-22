package com.skycong.logrecord.service;

import com.skycong.logrecord.core.LogRecord;
import com.skycong.logrecord.core.LogRecordAspect;
import com.skycong.logrecord.pojo.LogRecordPojo;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 自定义处理逻辑
 *
 * @author ruanmingcong 2022.4.22 下午 10:30
 */
public interface LogRecordService {

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
     * 添加自定义函数到 {@link LogRecordAspect.LogRecordEvaluationContext} 中，以便SpEl 可调用
     *
     * @return map[名称，方法]
     */
    default Map<String, Method> getFunctions() {
        return new HashMap<>(0);
    }

    /**
     * 记录日志
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
}
