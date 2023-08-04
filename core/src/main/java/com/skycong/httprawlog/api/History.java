package com.skycong.httprawlog.api;

import java.io.Serializable;

/**
 * @author ruanmingcong (005163)
 * @since 23/08/04 11:02
 */
public class History implements Serializable {

    private static final long serialVersionUID = -805223160856032952L;

    private String time;
    private String hLogId;
    private String method;
    private String url;
    private String requestHeaders;
    private String queryString;
    private String requestBody;
    private String responseStatus;
    private String responseHeaders;
    private String responseBody;

    public History() {
    }

    public History(String time, String hLogId, String method, String url, String requestHeaders, String queryString, String requestBody, String responseStatus, String responseHeaders, String responseBody) {
        this.time = time;
        this.hLogId = hLogId;
        this.method = method;
        this.url = url;
        this.requestHeaders = requestHeaders;
        this.queryString = queryString;
        this.requestBody = requestBody;
        this.responseStatus = responseStatus;
        this.responseHeaders = responseHeaders;
        this.responseBody = responseBody;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String gethLogId() {
        return hLogId;
    }

    public void sethLogId(String hLogId) {
        this.hLogId = hLogId;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(String requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public String getQueryString() {
        return queryString;
    }

    public void setQueryString(String queryString) {
        this.queryString = queryString;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(String requestBody) {
        this.requestBody = requestBody;
    }

    public String getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(String responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(String responseHeaders) {
        this.responseHeaders = responseHeaders;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("History{");
        sb.append("time='").append(time).append('\'');
        sb.append(", hLogId='").append(hLogId).append('\'');
        sb.append(", method='").append(method).append('\'');
        sb.append(", url='").append(url).append('\'');
        sb.append(", requestHeaders='").append(requestHeaders).append('\'');
        sb.append(", queryString='").append(queryString).append('\'');
        sb.append(", requestBody='").append(requestBody).append('\'');
        sb.append(", responseStatus='").append(responseStatus).append('\'');
        sb.append(", responseHeaders='").append(responseHeaders).append('\'');
        sb.append(", responseBody='").append(responseBody).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
