package com.skycong.httprawlog.filter;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StreamUtils;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;

/**
 * 控制台打印日志示例：
 *
 * <pre>
 * -------------- http raw data sessionId:	8C6A1D4433A10BFED5D64516939904E5
 * [url]:POST	/test/upload/p
 * [headers]:{host=localhost:8943, referer=null, content-type=null, cookie=JSESSIONID=1E60F913C20FFB23354FC404C86759F3, accept-language=null, user-agent=PostmanRuntime/7.28.4}
 * [request data]:	#query string# a=[b], c=[d], e[0]=[0], e[1]=[1],
 * #request body#
 * {
 *      test
 * }
 * [response data]:httpStatus=200	 responseHeaders={content-type=text/plain;charset=UTF-8}
 * OK
 * </pre>
 *
 * @author ruanmingcong
 * @version 1.0
 * @since 2020/7/1 16:54
 */
public class HttpRawLogFilter implements Filter {

    ///////////////////////////////////////////////////////////////////////////
    // Constant
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 表单请求，请求体如下
     * <pre>
     * ----------------------------661737071028691801410130
     * Content-Disposition: form-data; name="form1"
     *
     * form-data-
     * ----------------------------661737071028691801410130
     * Content-Disposition: form-data; name="form2"
     *
     * 含自费啊1123
     * ----------------------------661737071028691801410130
     * Content-Disposition: form-data; name="file"; filename="v2-2682bb9c3675ae1dbb0cb752bda61532_720w.jpg"
     * Content-Type: image/jpeg
     * </pre>
     * 所以请求中存在普通表单key=value，也有可能存在文件流，需要对其近特殊处理，否则log 将直接打印出原始文件字节流
     */
    private static final String FORM_DATA = "multipart/form-data";
    /**
     * 普通表单数据，请求体如下
     * <pre>
     *     form1=form-data-&form2=%E5%90%AB%E8%87%AA%E8%B4%B9%E5%95%8A1123
     * </pre>
     * 表单数据被URL编码了，需要解码操作
     */
    private static final String FORM_URLENCODED = "application/x-www-form-urlencoded";

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRawLogFilter.class);
    private static final String S0 = ",";
    private static final String UTF8 = "utf-8";


    /**
     * 需要打印的请求头
     */
    private String[] logHeaders;
    private List<String> urlExcludeSuffix;
    /**
     * query string 是否需要重新编码
     */
    private boolean queryStringEncode;

    @Override
    public void init(FilterConfig filterConfig) {
        String urlExcludeSuffix1 = filterConfig.getInitParameter("urlExcludeSuffix");
        String[] split1 = urlExcludeSuffix1.split(S0);
        urlExcludeSuffix = Arrays.stream(split1).filter(f -> !f.trim().isEmpty()).collect(Collectors.toList());

        String logHeaders1 = filterConfig.getInitParameter("logHeaders");
        if (logHeaders1 == null || logHeaders1.isEmpty()) {
            logHeaders1 = "content-type";
        }
        String[] split2 = logHeaders1.split(S0);
        List<String> strings = Arrays.stream(split2).filter(f -> !f.trim().isEmpty()).collect(Collectors.toList());
        this.logHeaders = new String[strings.size()];
        strings.toArray(this.logHeaders);
        queryStringEncode = Boolean.parseBoolean(filterConfig.getInitParameter("queryStringEncode"));
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!LOGGER.isDebugEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest request1 = (HttpServletRequest) request;
            String requestURI = request1.getRequestURI();
            int i = requestURI.lastIndexOf('.');
            // 不是第一个也不是最后一个字符
            if (i > 0 && i != requestURI.length() - 1) {
                String suffix = requestURI.substring(i + 1);
                if (urlExcludeSuffix.contains(suffix)) {
                    chain.doFilter(request, response);
                    return;
                }
            }
            printLog((HttpServletRequest) request, (HttpServletResponse) response, chain);
        } else {
            LOGGER.warn("request or response is not instanceof httpServletRequest or httpServletResponse, so not print http raw log");
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
    }


    private void printLog(HttpServletRequest request0, HttpServletResponse response0, FilterChain chain) throws IOException, ServletException {
        // 包装request 和 response，以便getInputStream 可重复读
        MyRequestWrapper request = new MyRequestWrapper(request0, queryStringEncode);
        MyResponseWrapper response = new MyResponseWrapper(response0);
        // val
        String sessionId, method, requestURL, requestHeaders, queryString, requestBody = null, responseBody, httpStatus, responseHeaders;
        sessionId = request.getSession().getId();
        method = request.getMethod();
        requestURL = request.getRequestURI();
        Map<String, String> headMap = new HashMap<>(logHeaders.length);
        for (String logHeader : logHeaders) {
            headMap.put(logHeader, request.getHeader(logHeader));
        }
        requestHeaders = headMap.toString();
        // query string
        queryString = request.getQueryString();
        // requestBody
        requestBody = request.getRequestBody();
        chain.doFilter(request, response);
        // 将response steam 重新写入返回
        byte[] bytes = response.getByteArrayOutputStream();
        response0.getOutputStream().write(bytes);
        response0.getOutputStream().flush();

        // 排除resp 的流
        String responseWrapperContentType = response.getContentType();
        String header1 = response.getHeader("Content-Disposition");
        if ("APPLICATION/OCTET-STREAM".equalsIgnoreCase(responseWrapperContentType) || header1 != null) {
            String s = "resp type : application/octet stream, so not log , response body size = " + bytes.length;
            responseBody = new String(s.getBytes());
        } else {
            responseBody = new String(bytes);
        }
        httpStatus = String.valueOf(response.getStatus());
        Map<String, String> respHeadMap = new HashMap<>(logHeaders.length);
        for (String logHeader : logHeaders) {
            String header = response.getHeader(logHeader);
            if (header != null && !header.isEmpty()) {
                respHeadMap.put(logHeader, header);
            }
        }
        responseHeaders = respHeadMap.toString();
        String requestLog = "";
        if (!queryString.isEmpty()) {
            requestLog = "#query string# " + queryString;
        }
        if (!requestBody.isEmpty()) {
            requestLog += "\n#request body#\n" + requestBody;
        }

        //log
        String sb = "\n --------------> http raw data sessionId:\t" + sessionId +
                "\n[url]:" + method + "\t" + requestURL +
                "\n[headers]:" + requestHeaders +
                "\n[request data]:\t" + requestLog +
                "\n[response data]:httpStatus=" + httpStatus + "\t responseHeaders=" + responseHeaders + "\n" + responseBody + "\n";
        LOGGER.debug(sb);
    }


    /**
     * 请求包装，post请求时，读取requestBody中的数据，将inputStream 复制并读取
     */
    private static class MyRequestWrapper extends HttpServletRequestWrapper {
        /**
         * 请求体
         */
        private byte[] bytes;

        private Map<String, String[]> map;

        private final boolean queryStringEncode;

        MyRequestWrapper(HttpServletRequest request, boolean queryStringEncode) {
            super(request);
            this.queryStringEncode = queryStringEncode;
            // 必须先读取 ParameterMap ，后读取 request.getInputStream() link：https://www.jianshu.com/p/0586e757e0af
            try {
                bytes = StreamUtils.copyToByteArray(super.getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

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
            if (map == null) {
                map = _getParameterMap();
            }
            return map;
        }

        @Override
        public String getQueryString() {
            String queryString = super.getQueryString();
            try {
                queryString = URLDecoder.decode(queryString, UTF8);
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            return queryString.replace("&", ", ");
        }

        private Map<String, String[]> _getParameterMap() {
            Map<String, String[]> originalMap = super.getParameterMap();
            Map<String, String[]> newMap = new HashMap<>();
            for (String key : originalMap.keySet()) {
                String[] values = originalMap.get(key);
                if (queryStringEncode) {
                    for (int i = 0; i < values.length; i++) {
                        // values[i] = new String(values[i].getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                        // try {
                        //     values[i] = URLDecoder.decode(values[i], UTF8);
                        // } catch (UnsupportedEncodingException e) {
                        //     e.printStackTrace();
                        // }
                    }
                }
                newMap.put(key, values);
            }
            return newMap;
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

        String getRequestBody() {
            String requestBodyOriginString = new String(bytes, StandardCharsets.UTF_8);
            if (requestBodyOriginString.isEmpty()) return requestBodyOriginString;
            String contentType = this.getContentType();
            if (contentType == null || contentType.isEmpty())
                return requestBodyOriginString;
            if (contentType.equalsIgnoreCase(FORM_URLENCODED)) {
                try {
                    String decode = URLDecoder.decode(requestBodyOriginString, UTF8);
                    // log 换行，方便查看
                    return decode.replace("&", "\n");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    return requestBodyOriginString;
                }
            } else if (contentType.toLowerCase().startsWith(FORM_DATA)) {
                String[] split = contentType.split(";");
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
    }

    /**
     * 响应包装，将响应流复制并读取
     */
    private static class MyResponseWrapper extends HttpServletResponseWrapper {

        private final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        private PrintWriter printWriter;

        MyResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(WriteListener listener) {

                }

                @Override
                public void write(int b) throws IOException {
                    byteArrayOutputStream.write(b);
                }
            };
        }

        /**
         * 重写父类的 getWriter() 方法，将响应数据缓存在 PrintWriter 中
         */
        @Override
        public PrintWriter getWriter() {
            printWriter = new PrintWriter(new OutputStreamWriter(byteArrayOutputStream, StandardCharsets.UTF_8));
            return printWriter;
        }

        /**
         * 获取缓存在 PrintWriter 中的响应数据
         *
         * @return byte[]
         */
        byte[] getByteArrayOutputStream() {
            if (null != printWriter) {
                printWriter.close();
                return byteArrayOutputStream.toByteArray();
            }
            try {
                byteArrayOutputStream.flush();
            } catch (IOException e) {
                // ignore
            }
            return byteArrayOutputStream.toByteArray();
        }
    }
}