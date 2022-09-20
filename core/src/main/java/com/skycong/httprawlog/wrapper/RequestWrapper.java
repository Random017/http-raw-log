package com.skycong.httprawlog.wrapper;

import com.skycong.httprawlog.Constant;
import org.springframework.util.StreamUtils;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * 缓存 request 中的 inputStream， 由于inputStream只能读取一次就会终止，需要将其包装成一个可重复读取的 inputStream
 * 重写一些方法，使得log 更友好
 * 重写一些方法，处理 query string，x-www-form-urlencoded 请求时字符编解码问题
 *
 * @author ruanmingcong (005163)
 * @since 2022/8/11 15:47
 */
public class RequestWrapper extends HttpServletRequestWrapper {

    /**
     * Constructs a request object wrapping the given request.
     *
     * @param formDataEncodeFlag form-data  是否需要重新编码(0: 自动判断，1：始终需要编码，2：始终不编码)
     * @param request            The request to wrap
     * @throws IllegalArgumentException if the request is null
     */
    public RequestWrapper(HttpServletRequest request, int formDataEncodeFlag) {
        super(request);
        // 必须先读取 ParameterMap ，后读取 request.getInputStream() link：https://www.jianshu.com/p/0586e757e0af
        map = packageParameterMapAll(super.getParameterMap(), getQueryStringMap(super.getQueryString()), isParameterEncode(formDataEncodeFlag));
        try {
            // 复制request body  inputStream
            bytes = StreamUtils.copyToByteArray(request.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 请求体
     */
    private byte[] bytes;

    /**
     * ParameterMap
     */
    private final Map<String, String[]> map;

    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return false;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
            }

            @Override
            public int read() {
                return byteArrayInputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream()));
    }

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
     * 获取request body log string
     */
    public String getRequestBodyLogString() {
        String requestBodyOriginString = "";
        try {
            requestBodyOriginString = StreamUtils.copyToString(this.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (requestBodyOriginString.isEmpty()) return requestBodyOriginString;
        String contentType = this.getContentType();
        if (contentType == null || contentType.isEmpty())
            return requestBodyOriginString;
        if (contentType.equalsIgnoreCase(Constant.FORM_URLENCODED)) {
            try {
                String decode = URLDecoder.decode(requestBodyOriginString, Constant.UTF8);
                // log 换行，方便查看
                return decode.replace(Constant.STRING0, Constant.LINE);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return requestBodyOriginString;
            }
        } else if (contentType.toLowerCase().startsWith(Constant.FORM_DATA)) {
            String[] split = contentType.split(Constant.STRING3);
            if (split.length != 2) {
                return contentType + "request body cannot resolve，it not contain like this ‘boundary=--------------------------028952705751347888065070’ , size = " + requestBodyOriginString.getBytes().length;
            }
            String boundary = split[1];
            int i = boundary.indexOf("=");
            if (i < 0) {
                return contentType + "request body cannot resolve，it not contain like this ‘boundary=--------------------------028952705751347888065070’ , size = " + requestBodyOriginString.getBytes().length;
            }
            boundary = boundary.substring(i + 1);
            if (boundary.isEmpty()) {
                return contentType + "request body cannot resolve，boundary is empty, size = " + requestBodyOriginString.getBytes().length;
            }
            String[] formData = requestBodyOriginString.split(boundary);
            StringBuilder formDataString = new StringBuilder();
            for (String formDatum : formData) {
                // 检查是否包含文件
                if (formDatum.contains("Content-Type:") && formDatum.contains("filename=")) {
                    int j1 = formDatum.indexOf("Content-Type:");
                    int ln = formDatum.indexOf("\n", j1 + 1);
                    String head = formDatum.substring(0, ln - 1);
                    // 文件大小估计值
                    int fileLength = formDatum.substring(ln).getBytes(StandardCharsets.ISO_8859_1).length;
                    String s = head + "\tfile size = " + fileLength + "\n--";
                    formDataString.append(s);
                } else {
                    formDataString.append(formDatum);
                }
            }
            return formDataString.toString();
        } else {
            //例如：application/json, application/xml, text/plain 等等，直接返回
            return requestBodyOriginString;
        }
    }

    /**
     * 获取全部参数map，包含form-data数据和query string参数
     *
     * @param originParameterMap 源getParameterMap
     * @param queryStringMap     queryStringMap
     * @param parameterEncode    parameterEncode {{@link #isParameterEncode(int)}}
     * @return packageParameterMapAll
     */
    public static Map<String, String[]> packageParameterMapAll(Map<String, String[]> originParameterMap, Map<String, String[]> queryStringMap, boolean parameterEncode) {
        // 参数map包含form-data参数和query string 参数
        // form-data依据 getCharacterEncoding 来判断是否需要重新使用utf-8编码，query string 使用URLDecode
        Map<String, String[]> newMap = new HashMap<>();
        for (String key : originParameterMap.keySet()) {
            String[] values = originParameterMap.get(key);
            // form-data 默认使用的是 ISO_8859_1 编码，需要重新编码，如果content-type 中指定字符类型，例如：application/x-www-form-urlencoded;charset=utf-8，则使用utf-8编码
            if (parameterEncode) {
                for (int i = 0; i < values.length; i++) {
                    values[i] = new String(values[i].getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                }
            }
            newMap.put(key, values);
        }
        // 原始表单参数，包含query string 的参数，但是query string 使用的是 URLEncode，如果直接使用 ISO_8859_1 to UTF-8 的话会造成乱码
        // 将 query string map 的值重新put
        newMap.putAll(queryStringMap);
        return newMap;
    }

    /**
     * 获取query string，解码后并将其封装成 map，多个相同的query string key在map的值数组里
     *
     * @param originQueryString 原始query string
     * @return Map<String, String [ ]>
     */
    public static Map<String, String[]> getQueryStringMap(String originQueryString) {
        String queryString = originQueryString;
        Map<String, String[]> map = new HashMap<>();
        if (queryString == null || queryString.isEmpty()) return map;
        try {
            // query string 使用URL encode，需要解码
            queryString = URLDecoder.decode(queryString, Constant.UTF8);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String[] kvs = queryString.split(Constant.STRING0);
        if (kvs.length == 0) {
            return map;
        }
        for (String kv : kvs) {
            if (kv != null && !kv.isEmpty()) {
                String[] kv0 = kv.split(Constant.EQ);
                if (kv0.length == 2) {
                    String k = kv0[0];
                    String v = kv0[1];
                    String[] vs = map.get(k);
                    if (vs == null) {
                        // 没有创建
                        vs = new String[]{v};
                    } else {
                        // 已存在，扩容1
                        String[] tmp = new String[vs.length + 1];
                        System.arraycopy(vs, 0, tmp, 0, vs.length);
                        tmp[vs.length] = v;
                        vs = tmp;
                    }
                    map.put(k, vs);
                } else if (kv0.length == 1) {
                    map.put(kv0[0], new String[0]);
                }
            }
        }
        return map;
    }


    /**
     * @param parameterMapAll 原始query string
     * @return 获取query string URLDecode 后友好打印log
     */
    public static String queryStringLog(Map<String, String[]> parameterMapAll) {
        Set<Map.Entry<String, String[]>> entries = parameterMapAll.entrySet();
        StringBuilder stringBuilder = new StringBuilder();
        for (Map.Entry<String, String[]> entry : entries) {
            String key = entry.getKey();
            String[] value = entry.getValue();
            stringBuilder.append(key)
                    .append(Constant.EQ)
                    .append(Arrays.toString(value))
                    .append(Constant.SPLIT);
        }
        return stringBuilder.toString();
    }


}
