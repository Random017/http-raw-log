package com.skycong.httprawlog.wrapper;

import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.io.IOException;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
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
    public FormRequestWrapper(HttpServletRequest request, boolean parameterEncode) throws MultipartException, ServletException, IOException {
        super(request);
        Map<String, String[]> fileMap = resolveParamsFromPart();
        Map<String, String[]> queryStringMap = RequestWrapper.getQueryStringMap(super.getQueryString());
        queryStringMap.putAll(fileMap);
        this.map = RequestWrapper.packageParameterMapAll(super.getParameterMap(), queryStringMap, parameterEncode);
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

    /**
     * 从 part中解析表单参数
     */
    private Map<String, String[]> resolveParamsFromPart() throws ServletException, IOException {
        HashMap<String, String[]> map = new HashMap<>();
        Collection<Part> parts = getParts();
        if (parts == null || parts.isEmpty())
            return map;
        for (Part part : parts) {
            String contentType = part.getContentType();
            if (contentType == null || contentType.isEmpty()) continue;
            String k = part.getName();
            String v = String.format("[filename=%s ,content-type=%s ,filesize=%d]", part.getSubmittedFileName(), contentType, part.getSize());
            map.put(k, new String[]{v});
        }
        return map;
    }

}
