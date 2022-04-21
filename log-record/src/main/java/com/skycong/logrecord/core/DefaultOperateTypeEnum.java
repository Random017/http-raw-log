package com.skycong.logrecord.core;

/**
 * @author ruanmingcong (005163) on 2022/4/21 18:08
 */
public enum DefaultOperateTypeEnum  OperateType {

    /**
     * 添加操作
     */
    ADD,
    /**
     * 修改操作
     */
    UPDATE,
    /**
     * 删除操作
     */
    DELETE,
    /**
     * 导出操作
     */
    EXPORT,
    /**
     * 导入操作
     */
    IMPORT,
    /**
     * 同步操作
     */
    SYNC,
    ;

    @Override
    public String operateType() {
        return this.name().toLowerCase();
    }

}
