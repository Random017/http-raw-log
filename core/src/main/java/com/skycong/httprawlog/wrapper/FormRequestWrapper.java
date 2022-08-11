package com.skycong.httprawlog.wrapper;

import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Map;
import java.util.Vector;

/**
 * 处理 multipart/form-data 格式请求，这种类型请求伴随这文件上传和参数传递
 * 获取form-data中除文件之外的参数
 *
 * @author ruanmingcong (005163)
 * @since 2022/8/11 15:47
 */
public class FormRequestWrapper extends StandardMultipartHttpServletRequest {


    /**
     * Constructs a request object wrapping the given request.
     *
     * @param parameterEncode 表单数据是否需要重新编码 ，StandardCharsets.ISO_8859_1  ==> StandardCharsets.UTF_8
     * @param request         The request to wrap
     * @throws IllegalArgumentException if the request is null
     */
    public FormRequestWrapper(HttpServletRequest request, boolean parameterEncode) throws MultipartException {
        super(request);
        map = RequestWrapper.packageParameterMapAll(super.getParameterMap(), RequestWrapper.getQueryStringMap(super.getQueryString()), parameterEncode);
    }

    /**
     * ParameterMap
     */
    private final Map<String, String[]> map;


    @Override
    public Map<String, String[]> getParameterMap() {
        return map;
    }

    @Override
    public String getParameter(String name) {
        String[] parameterValues = getParameterValues(name);
        if (parameterValues != null && parameterValues.length > 0) {
            return parameterValues[0];
        } else {
            return null;
        }
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return new Vector<>(map.keySet()).elements();
    }

    @Override
    public String[] getParameterValues(String name) {
        return map.get(name);
    }

}
