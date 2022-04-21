    package com.skycong.logrecord.filter;


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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 控制台打印日志示例：
 * <p>
 * -------------- http raw data sessionId:	8C6A1D4433A10BFED5D64516939904E5
 * [url]:POST	/test/upload/p
 * [headers]:{host=localhost:8943, referer=null, content-type=null, cookie=JSESSIONID=1E60F913C20FFB23354FC404C86759F3, accept-language=null, user-agent=PostmanRuntime/7.28.4}
 * [request data]:	#query string# a=[b], c=[d], e[0]=[0], e[1]=[1],
 * #request body#
 * {
 * safdsafsdaff
 * }
 * [response data]:httpStatus=200	 responseHeaders={content-type=text/plain;charset=UTF-8}
 * OK
 * </p>
 *
 * @author ruanmingcong
 * @version 1.0
 * @since 2020/7/1 16:54
 */
public class HttpRawLogFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRawLogFilter.class);

    /**
     * 需要打印的请求头
     */
    private String[] logHeaders;

    @Override
    public void init(FilterConfig filterConfig) {
        String logHeaders1 = filterConfig.getInitParameter("logHeaders");
        if (logHeaders1 == null || logHeaders1.isEmpty()) {
            logHeaders1 = "content-type";
        }
        String[] split2 = logHeaders1.split(",");
        List<String> strings = Arrays.stream(split2).filter(f -> !f.trim().isEmpty()).collect(Collectors.toList());
        this.logHeaders = new String[strings.size()];
        strings.toArray(this.logHeaders);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (!LOGGER.isDebugEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            printLog((HttpServletRequest) request, (HttpServletResponse) response, chain);
        } else {
            LOGGER.warn("request or response is not instanceof httpServletRequest or httpServletResponse, so not print http raw log");
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {
    }


    private void printLog(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {

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
        Map<String, String[]> queryStringMap = Optional.ofNullable(request.getParameterMap()).orElse(new HashMap<>());
        StringBuilder queryStringSb = new StringBuilder();
        Set<Map.Entry<String, String[]>> entrySet = queryStringMap.entrySet();
        for (Map.Entry<String, String[]> entry : entrySet) {
            queryStringSb.append(entry.getKey())
                    .append("=")
                    .append(Arrays.toString(entry.getValue()))
                    .append(", ");
        }
        queryString = queryStringSb.toString();

        boolean needDealRequestBody = true;
        // 上传文件流 ignore
        String contentType = request.getContentType();
        if (contentType != null && !contentType.isEmpty() && contentType.toLowerCase().startsWith("multipart/form-data")) {
            requestBody = "request body is file stream, so not log it";
            needDealRequestBody = false;
        }

        MyResponseWrapper myResponseWrapper = new MyResponseWrapper(response);
        if (needDealRequestBody) {
            MyRequestWrapper myRequestWrapper = new MyRequestWrapper(request);
            requestBody = myRequestWrapper.getRequestBody();
            chain.doFilter(myRequestWrapper, myResponseWrapper);
        } else {
            chain.doFilter(request, myResponseWrapper);
        }
        // 将response steam 重新写入返回
        byte[] bytes = myResponseWrapper.getByteArrayOutputStream();
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();

        // 排除resp 的流
        String responseWrapperContentType = myResponseWrapper.getContentType();
        String header1 = myResponseWrapper.getHeader("Content-Disposition");
        if ("APPLICATION/OCTET-STREAM".equalsIgnoreCase(responseWrapperContentType) || header1 != null) {
            String s = "resp type : application/octet stream, so not log , response body size = " + bytes.length;
            responseBody = new String(s.getBytes());
        } else {
            responseBody = new String(bytes);
        }
        httpStatus = String.valueOf(myResponseWrapper.getStatus());
        Map<String, String> respHeadMap = new HashMap<>(logHeaders.length);
        for (String logHeader : logHeaders) {
            String header = myResponseWrapper.getHeader(logHeader);
            if (header != null && !header.isEmpty()) {
                respHeadMap.put(logHeader, header);
            }
        }
        responseHeaders = respHeadMap.toString();
        String requestLog = "";
        if (!queryString.isEmpty()) {
            requestLog = "#query string# " + queryString;

        }
        if (requestBody != null && !requestBody.isEmpty()) {
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
        private String requestBody = "";

        MyRequestWrapper(HttpServletRequest request) {
            super(request);
            try {
                requestBody = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public ServletInputStream getInputStream() {

            final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(this.getRequestBody().getBytes());

            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return false;
                }

                @Override
                public boolean isReady() {
                    return false;
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
            return new BufferedReader(new InputStreamReader(this.getInputStream()));
        }

        String getRequestBody() {
            return this.requestBody;
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
                    return false;
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