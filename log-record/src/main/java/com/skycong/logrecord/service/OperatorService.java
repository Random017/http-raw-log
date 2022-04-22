package com.skycong.logrecord.service;

import com.skycong.logrecord.core.LogRecord;

/**
 * 获取操作人信息接口 ,实现类需要加入到spring 容器中
 *
 * @author ruanmingcong (005163) on 2022/4/22 15:38
 */
public interface OperatorService {

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
}
