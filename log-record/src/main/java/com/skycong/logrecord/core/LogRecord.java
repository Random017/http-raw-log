package com.skycong.logrecord.core;

import com.skycong.logrecord.constant.DefaultOperateTypeEnum;
import com.skycong.logrecord.service.OperatorService;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 注解
 *
 * <a href="https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#expressions">SpEL</a>
 *
 * @author ruanmingcong (005163) on 2022/4/21 17:53
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
@Documented
public @interface LogRecord {

    /**
     * 日志记录模板 ，支持 SpEL 表达式 ,其中 {#_ret} 为方法返回结果对象 {#_errMsg} 方法执行异常时信息
     */
    String value();

    /**
     * 操作人信息，支持 SpEL 表达式
     * <p>
     * 可以实现接口 {@link OperatorService#getCurrentOperator(String)} 以重写获取操作人方法
     * </p>
     */
    String operator() default "";

    /**
     * 操作类型，常量 参考{@link DefaultOperateTypeEnum}
     */
    String operateType() default "";

    /**
     * 业务类型，必填，常量
     */
    String businessType();

    /**
     * 业务数据标识ID，支持 SpEL 表达式
     */
    String businessDataId() default "";

    /**
     * 业务数据详情，支持 SpEL 表达式
     */
    String businessDataDetail() default "";
}
