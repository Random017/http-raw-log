package com.skycong.httprawlog.wrapper;

import com.skycong.httprawlog.Constant;
import org.apache.catalina.core.ApplicationPart;
import org.apache.tomcat.util.http.fileupload.FileItem;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.StandardMultipartHttpServletRequest;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.io.IOException;
import java.lang.reflect.Field;
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
     * @param formDataEncodeFlag form-data  是否需要重新编码(0: 自动判断，1：始终需要编码，2：始终不编码)
     * @param request            The request to wrap
     * @throws IllegalArgumentException if the request is null
     */
    public FormRequestWrapper(HttpServletRequest request, int formDataEncodeFlag) throws MultipartException, ServletException, IOException {
        super(request);
        Map<String, String[]> fileMap = resolveParamsFromPart();
        Map<String, String[]> queryStringMap = RequestWrapper.getQueryStringMap(super.getQueryString());
        queryStringMap.putAll(fileMap);
        this.map = RequestWrapper.packageParameterMapAll(super.getParameterMap(), queryStringMap, isParameterEncode(formDataEncodeFlag));
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
     * form-data 参数是否需要重新编码，依据 getCharacterEncoding 判断
     *
     * @param formDataEncodeFlag form-data  是否需要重新编码(0: 自动判断，1：始终需要编码，2：始终不编码)
     * @return true 是，false 否
     */
    public boolean isParameterEncode(int formDataEncodeFlag) {
        if (formDataEncodeFlag == 1) {
            return true;
        } else if (formDataEncodeFlag == 2) {
            return false;
        } else {
            String enc = super.getCharacterEncoding();
            return enc == null || enc.equalsIgnoreCase(Constant.DEFAULT_CHARACTER_ENCODING);
        }
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
            // part 是否是一个有效的文件
            boolean isFile;
            //  Servlet 3.0 upload API that expects Parts.
            if (part instanceof ApplicationPart) {
                ApplicationPart applicationPart = (ApplicationPart) part;
                Field fileItem = ReflectionUtils.findField(ApplicationPart.class, "fileItem");
                assert fileItem != null;
                ReflectionUtils.makeAccessible(fileItem);
                FileItem value = (FileItem) ReflectionUtils.getField(fileItem, applicationPart);
                assert value != null;
                // 是否是一个表单属性
                isFile = !value.isFormField();
                ReflectionUtils.clearCache();
            } else {
                // others
                isFile = part.getContentType() != null && part.getSubmittedFileName() != null;
            }
            if (isFile) {
                String k = part.getName();
                String v = String.format("[filename=%s ,content-type=%s ,filesize=%d]", part.getSubmittedFileName(), part.getContentType(), part.getSize());
                map.put(k, new String[]{v});
            }
        }
        return map;
    }

}
