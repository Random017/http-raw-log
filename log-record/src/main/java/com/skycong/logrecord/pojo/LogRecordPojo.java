package com.skycong.logrecord.pojo;

import com.skycong.logrecord.core.LogRecord;

import java.io.Serializable;

/**
 * {@link LogRecord} 对应的POJO
 *
 * @author ruanmingcong (005163) on 2022/4/22 13:09
 */
public class LogRecordPojo implements Serializable {

    private static final long serialVersionUID = -8240783194228221967L;

    /**
     * 日志记录模板
     */
    private String value;

    /**
     * 操作人信息
     */
    private String operator;

    /**
     * 操作类型
     */
    private String operateType;

    /**
     * 业务类型
     */
    private String businessType;

    /**
     * 业务数据标识ID
     */
    private String businessDataId;

    /**
     * 业务数据详情
     */
    private String businessDataDetail;

    public LogRecordPojo() {
    }

    public LogRecordPojo(String value, String operator, String operateType, String businessType, String businessDataId, String businessDataDetail) {
        this.value = value;
        this.operator = operator;
        this.operateType = operateType;
        this.businessType = businessType;
        this.businessDataId = businessDataId;
        this.businessDataDetail = businessDataDetail;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getOperateType() {
        return operateType;
    }

    public void setOperateType(String operateType) {
        this.operateType = operateType;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public String getBusinessDataId() {
        return businessDataId;
    }

    public void setBusinessDataId(String businessDataId) {
        this.businessDataId = businessDataId;
    }

    public String getBusinessDataDetail() {
        return businessDataDetail;
    }

    public void setBusinessDataDetail(String businessDataDetail) {
        this.businessDataDetail = businessDataDetail;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("LogRecordModel{");
        sb.append("value='").append(value).append('\'');
        sb.append(", operator='").append(operator).append('\'');
        sb.append(", operateType='").append(operateType).append('\'');
        sb.append(", businessType='").append(businessType).append('\'');
        sb.append(", businessDataId='").append(businessDataId).append('\'');
        sb.append(", businessDataDetail='").append(businessDataDetail).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
