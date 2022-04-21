package com.skycong.logrecord.core;

/**
 * @author ruanmingcong (005163) on 2022/4/21 17:53
 */
public @interface LogRecord {
    /**
     * 日志记录模板
     */
    String value();

    /**
     * 操作人信息
     */
    String operator() default "";

    /**
     * 操作类型
     */
    OperateType operateType() default OperateType.EMPTY;
    OperateType operateType() default OperateType.EMPTY;

    /**
     * 业务类型
     */
    String businessType();

    /**
     * 业务数据标识ID
     */
    String businessDataId() default "";

    /**
     * 业务数据详情
     */
    String businessDataDetail() default "";
}
