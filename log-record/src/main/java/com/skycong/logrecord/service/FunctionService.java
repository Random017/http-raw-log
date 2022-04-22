package com.skycong.logrecord.service;

import com.skycong.logrecord.core.LogRecordAspect;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * <a href="https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions-ref-functions">functions</a>
 *
 * @author ruanmingcong (005163) on 2022/4/22 18:15
 */
public interface FunctionService {

    /**
     * 添加自定义函数到 {@link LogRecordAspect.LogRecordEvaluationContext} 中，以便SpEl 可调用
     *
     * @return map[名称，方法]
     */
    Map<String, Method> getFunctions();
}
