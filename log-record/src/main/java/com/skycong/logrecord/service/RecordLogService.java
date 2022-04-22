package com.skycong.logrecord.service;

import com.skycong.logrecord.pojo.LogRecordPojo;

/**
 * 切面产生的日志将会回调这个方法传入，随后自定义处理逻辑,实现类需要加入到spring 容器中
 *
 * @author ruanmingcong (005163) on 2022/4/22 15:57
 */
public interface RecordLogService {

    /**
     * 记录日志
     * <p>
     * 默认实现 log.debug(logRecordPojo) ,需要 {@link com.skycong.logrecord.core.LogRecordAspect} logging.level 设置为debug
     * </p>
     *
     * @param logRecordPojo {@link LogRecordPojo}
     */
    void record(LogRecordPojo logRecordPojo);
}
