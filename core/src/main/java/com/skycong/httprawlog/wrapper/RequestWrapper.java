package com.skycong.httprawlog.wrapper;

import com.skycong.httprawlog.Constant;
import com.skycong.httprawlog.filter.HttpRawLogFilter;
import com.skycong.httprawlog.util.CharsetSupport;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 缓存 request 中的 inputStream， 由于inputStream只能读取一次就会终止，需要将其包装成一个可重复读取的 inputStream
 * 重写一些方法，使得log 更友好
 * 重写一些方法，处理 query string，x-www-form-urlencoded 请求时字符编解码问题
 *
 * <p>请求体完整缓存并完整回放给业务；只有日志字符串会按 {@link #MAX_LOG_BODY_SIZE} 截断。
 *
 * @author ruanmingcong
 * @since 2022/8/11 15:47
 */
public class RequestWrapper extends HttpServletRequestWrapper {

    /**
     * 日志字符串中请求体的最大字节数，默认 4MB。业务侧拿到的请求体不受此限制。
     */
    private static final int MAX_LOG_BODY_SIZE = 4 * 1024 * 1024;

    /**
     * Content-Length 未知时的初始容量，8KB
     */
    private static final int INITIAL_BUFFER_SIZE = 8 * 1024;

    /**
     * 请求体（完整内容，用于回放给业务）
     */
    private final byte[] bytes;

    /**
     * ParameterMap
     */
    private final Map<String, String[]> map;

    /**
     * Constructs a request object wrapping the given request.
     *
     * @param formDataEncodeFlag form-data  是否需要重新编码(0: 自动判断，1：始终需要编码，2：始终不编码)
     * @param request            The request to wrap
     */
    public RequestWrapper(HttpServletRequest request, int formDataEncodeFlag) {
        super(request);
        // 必须先读取 body，再解析参数：真实容器下 getParameterMap() 会把 form body 消费掉
        bytes = readRequestBody(request);
        String contentType = request.getContentType();
        Map<String, String[]> originParameterMap;
        boolean parameterEncode;
        if (isFormUrlEncoded(contentType) && bytes.length > 0) {
            // form body 已由本类按原始字节解析，值已是最终字符串，不能再按容器默认编码二次转换
            originParameterMap = parseFormUrlEncoded(bytes, contentType, super.getCharacterEncoding());
            parameterEncode = false;
        } else {
            originParameterMap = super.getParameterMap();
            parameterEncode = isParameterEncode(formDataEncodeFlag);
        }
        map = packageParameterMapAll(originParameterMap, getQueryStringMap(super.getQueryString()), parameterEncode);
    }

    /**
     * 判断是否为 application/x-www-form-urlencoded。
     */
    private static boolean isFormUrlEncoded(String contentType) {
        return contentType != null && mediaType(contentType).equals(Constant.FORM_URLENCODED);
    }

    /**
     * 从已经缓存的原始字节解析 form-urlencoded 参数。
     *
     * <p>不能依赖容器的 getParameterMap()：它通常已经消费了 body，之后再读 inputStream 只会得到空内容。
     */
    private static Map<String, String[]> parseFormUrlEncoded(byte[] body, String contentType, String declaredEncoding) {
        Charset charset = formCharset(contentType, declaredEncoding);
        String text = new String(body, charset);
        Map<String, String[]> result = new LinkedHashMap<>();
        for (String pair : text.split(Constant.AMPERSAND, -1)) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String rawKey = eq >= 0 ? pair.substring(0, eq) : pair;
            String key = decodeFormComponent(rawKey, charset);
            if (key.isEmpty()) {
                continue;
            }
            if (eq < 0) {
                // Servlet 容器对没有 '=' 的参数返回空字符串，而不是“参数缺失”
                result.putIfAbsent(key, new String[]{""});
                continue;
            }
            String value = decodeFormComponent(pair.substring(eq + 1), charset);
            String[] values = result.get(key);
            if (values == null) {
                values = new String[]{value};
            } else {
                values = Arrays.copyOf(values, values.length + 1);
                values[values.length - 1] = value;
            }
            result.put(key, values);
        }
        return result;
    }

    /**
     * form-urlencoded 的默认编码按 UTF-8 处理；显式 charset 或非默认的容器编码优先。
     */
    private static Charset formCharset(String contentType, String declaredEncoding) {
        Charset explicit = CharsetSupport.charsetFromContentType(contentType);
        if (explicit != null) {
            return explicit;
        }
        Charset declared = charsetOrNull(declaredEncoding);
        if (declared != null
                && !StandardCharsets.ISO_8859_1.equals(declared)
                && !StandardCharsets.US_ASCII.equals(declared)) {
            return declared;
        }
        return StandardCharsets.UTF_8;
    }

    private static Charset charsetOrNull(String name) {
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        try {
            return Charset.forName(name.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String decodeFormComponent(String value, Charset charset) {
        try {
            return URLDecoder.decode(value, charset);
        } catch (IllegalArgumentException e) {
            HttpRawLogFilter.LOGGER.warn("form-urlencoded 参数解码失败，保留原始值: {}", value);
            return value;
        }
    }

    /**
     * 完整读取请求体。
     *
     * <p>业务侧收到的请求体必须完整，因此这里不做大小截断；日志侧由 {@link #getRequestBodyLogString()} 截断。
     *
     * @param request HTTP 请求
     * @return 完整的请求体字节数组
     */
    private byte[] readRequestBody(HttpServletRequest request) {
        try {
            InputStream is = request.getInputStream();
            int contentLength = request.getContentLength();
            if (contentLength >= 0) {
                // Content-Length 已知：精确分配，一次读完
                byte[] buf = new byte[contentLength];
                int offset = 0;
                while (offset < contentLength) {
                    int read = is.read(buf, offset, contentLength - offset);
                    if (read == -1) {
                        break;
                    }
                    if (read == 0) {
                        // 阻塞式 ServletInputStream 正常不会返回 0；兜底读一个字节避免空转
                        int single = is.read();
                        if (single == -1) {
                            break;
                        }
                        buf[offset++] = (byte) single;
                    } else {
                        offset += read;
                    }
                }
                return offset == contentLength ? buf : Arrays.copyOf(buf, offset);
            }

            // Content-Length 未知(-1)：按需扩容，直到读完
            byte[] buf = new byte[INITIAL_BUFFER_SIZE];
            int pos = 0;
            int len;
            while ((len = is.read(buf, pos, buf.length - pos)) != -1) {
                pos += len;
                if (pos == buf.length) {
                    buf = Arrays.copyOf(buf, buf.length * 2);
                }
            }
            return Arrays.copyOf(buf, pos);
        } catch (IOException e) {
            HttpRawLogFilter.LOGGER.error("读取request body失败", e);
            return new byte[0];
        }
    }

    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return byteArrayInputStream.available() == 0;
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

            @Override
            public int read(byte[] b, int off, int len) {
                return byteArrayInputStream.read(b, off, len);
            }

            @Override
            public long skip(long n) {
                return byteArrayInputStream.skip(n);
            }

            @Override
            public int available() {
                return byteArrayInputStream.available();
            }
        };
    }

    /**
     * 业务侧 reader 保持声明编码（JSON/XML 缺省 UTF-8，其余按容器声明，未声明按 Servlet 默认）。
     * 只有日志字符串使用 {@link CharsetSupport#resolve} 的 UTF-8 启发式，避免日志组件改变业务读取语义。
     */
    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(),
                CharsetSupport.resolveForReader(super.getContentType(), super.getCharacterEncoding())));
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        return map;
    }

    @Override
    public String getParameter(String name) {
        String[] parameterValues = getParameterValues(name);
        return (parameterValues != null && parameterValues.length > 0) ? parameterValues[0] : null;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(map.keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        return map.get(name);
    }

    /**
     * 请求体回放后长度与实际一致，避免使用方按原始 Content-Length 读取时卡住或读不全。
     */
    @Override
    public int getContentLength() {
        return bytes.length;
    }

    @Override
    public long getContentLengthLong() {
        return bytes.length;
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
     * 获取 request body log string（最多 {@link #MAX_LOG_BODY_SIZE} 字节，超出部分截断并标记）
     */
    public String getRequestBodyLogString() {
        int logLen = Math.min(bytes.length, MAX_LOG_BODY_SIZE);
        String requestBodyOriginString;
        try {
            requestBodyOriginString = new String(bytes, 0, logLen,
                    CharsetSupport.resolve(getContentType(), super.getCharacterEncoding(), bytes, logLen));
        } catch (Exception e) {
            HttpRawLogFilter.LOGGER.error("读取request body日志字符串失败", e);
            return "";
        }
        if (requestBodyOriginString.isEmpty()) {
            return requestBodyOriginString;
        }
        String contentType = this.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            return appendTruncatedSuffix(requestBodyOriginString);
        }
        String mediaType = mediaType(contentType);
        String result;
        if (mediaType.equals(Constant.FORM_URLENCODED)) {
            Charset charset = formCharset(contentType, super.getCharacterEncoding());
            try {
                String decode = URLDecoder.decode(requestBodyOriginString, charset);
                // log 换行，方便查看
                result = decode.replace(Constant.AMPERSAND, Constant.LINE);
            } catch (IllegalArgumentException e) {
                // 日志被上限截断时可能留下不完整的 %xx，保留原始 body 即可，不影响业务
                HttpRawLogFilter.LOGGER.warn("form-urlencoded 日志解码失败，保留原始 body: {}", e.toString());
                result = requestBodyOriginString;
            }
        } else if (mediaType.equals(Constant.FORM_DATA)) {
            result = formatMultipart(requestBodyOriginString, contentType);
        } else {
            //例如：application/json, application/xml, text/plain 等等，直接返回
            result = requestBodyOriginString;
        }
        return appendTruncatedSuffix(result);
    }

    private String appendTruncatedSuffix(String result) {
        if (bytes.length <= MAX_LOG_BODY_SIZE) {
            return result;
        }
        return result + "\n... [request body truncated, total size = " + bytes.length
                + " bytes > " + MAX_LOG_BODY_SIZE + ", only first " + MAX_LOG_BODY_SIZE + " bytes logged]";
    }

    /**
     * 格式化 multipart 请求体：文件字段只打印大小，避免把文件字节流写入日志。
     */
    private String formatMultipart(String requestBody, String contentType) {
        int boundaryIndex = contentType.toLowerCase(Locale.ROOT).indexOf("boundary=");
        if (boundaryIndex < 0) {
            return contentType + " request body cannot resolve, it does not contain boundary, size = " + requestBody.length();
        }
        String boundary = contentType.substring(boundaryIndex + "boundary=".length()).trim();
        if (boundary.isEmpty()) {
            return contentType + " request body cannot resolve, boundary is empty, size = " + requestBody.length();
        }
        String[] formData = requestBody.split(Pattern.quote(boundary));
        StringBuilder formDataString = new StringBuilder();
        for (String formDatum : formData) {
            // 检查是否包含文件
            int contentTypeIndex = formDatum.indexOf("Content-Type:");
            int filenameIndex = formDatum.indexOf("filename=");
            if (contentTypeIndex >= 0 && filenameIndex >= 0) {
                int headerEnd = formDatum.indexOf('\n', contentTypeIndex);
                if (headerEnd < 0) {
                    headerEnd = formDatum.length();
                }
                String head = formDatum.substring(0, headerEnd).trim();
                int bodyStart = Math.min(formDatum.length(), headerEnd + 1);
                // 旧实现按 ISO-8859-1 再编码取长度；每个 char 最多对应 1 字节，直接算字符数等价且不复制文件内容
                int fileLength = formDatum.length() - bodyStart;
                formDataString.append(head).append("\tfile size = ").append(fileLength).append("\n--");
            } else {
                formDataString.append(formDatum);
            }
        }
        return formDataString.toString();
    }

    /**
     * 取 Content-Type 的 media type 部分（去掉 charset 等参数），小写。
     */
    private static String mediaType(String contentType) {
        int index = contentType.indexOf(';');
        String type = index >= 0 ? contentType.substring(0, index) : contentType;
        return type.trim().toLowerCase(Locale.ROOT);
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
        if (originParameterMap != null) {
            for (Map.Entry<String, String[]> entry : originParameterMap.entrySet()) {
                String[] values = entry.getValue();
                if (values == null) {
                    newMap.put(entry.getKey(), null);
                    continue;
                }
                // 复制数组，避免原地修改容器持有的参数数组（否则业务侧会拿到被库重新编码后的值）
                String[] copied = new String[values.length];
                for (int i = 0; i < values.length; i++) {
                    String value = values[i];
                    if (parameterEncode && value != null) {
                        // form-data 默认使用的是 ISO_8859_1 编码，需要重新编码
                        value = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                    }
                    copied[i] = value;
                }
                newMap.put(entry.getKey(), copied);
            }
        }
        // 原始表单参数，包含query string 的参数，但是query string 使用的是 URLEncode，如果直接使用 ISO_8859_1 to UTF-8 的话会造成乱码
        // 将 query string map 的值重新put
        if (queryStringMap != null) {
            newMap.putAll(queryStringMap);
        }
        return newMap;
    }

    /**
     * 获取query string，解码后并将其封装成 map，多个相同的query string key在map的值数组里
     *
     * <p>先按 '&amp;' 拆分，再对 key/value 分别解码，避免值中的 %26 被错误拆分。
     *
     * @param originQueryString 原始query string
     * @return Map&lt;String, String[]&gt;
     */
    public static Map<String, String[]> getQueryStringMap(String originQueryString) {
        Map<String, String[]> map = new LinkedHashMap<>();
        if (originQueryString == null || originQueryString.isEmpty()) {
            return map;
        }
        String[] kvs = originQueryString.split(Constant.AMPERSAND);
        for (String kv : kvs) {
            if (kv == null || kv.isEmpty()) {
                continue;
            }
            int eq = kv.indexOf('=');
            String rawKey = eq >= 0 ? kv.substring(0, eq) : kv;
            String rawValue = eq >= 0 ? kv.substring(eq + 1) : null;
            String key = decode(rawKey);
            if (key.isEmpty()) {
                continue;
            }
            String[] vs = map.get(key);
            if (vs == null) {
                // 没有 '=' 的参数按 Servlet 语义是空字符串
                vs = rawValue == null ? new String[]{""} : new String[]{decode(rawValue)};
            } else if (rawValue != null) {
                String[] tmp = Arrays.copyOf(vs, vs.length + 1);
                tmp[vs.length] = decode(rawValue);
                vs = tmp;
            }
            map.put(key, vs);
        }
        return map;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, Constant.UTF8);
        } catch (UnsupportedEncodingException e) {
            HttpRawLogFilter.LOGGER.error("Query string URL解码失败", e);
            return value;
        }
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
