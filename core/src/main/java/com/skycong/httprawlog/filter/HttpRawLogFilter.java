package com.skycong.httprawlog.filter;


import com.skycong.httprawlog.Constant;
import com.skycong.httprawlog.api.History;
import com.skycong.httprawlog.api.HistoryRecord;
import com.skycong.httprawlog.wrapper.FormRequestWrapper;
import com.skycong.httprawlog.wrapper.RequestWrapper;
import com.skycong.httprawlog.wrapper.ResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
public class HttpRawLogFilter extends OncePerRequestFilter {

    public static final Logger LOGGER = LoggerFactory.getLogger(HttpRawLogFilter.class);

    /**
     * 需要打印的http header，包括request 和response
     */
    private Set<String> logHeaders;

    /**
     * 忽略的 url ，支持ant 路径格式
     */
    private Set<String> urlExcludePatterns;

    /**
     * form-data  是否需要重新编码(0: 自动判断，1：始终需要编码，2：始终不编码)
     */
    private int formDataEncodeFlag;

    private HistoryRecord historyRecord;

    public HttpRawLogFilter(HistoryRecord historyRecord) {
        this.historyRecord = historyRecord;
    }

    @Override
    protected void initFilterBean() {
        FilterConfig filterConfig = getFilterConfig();
        String logHeaders1 = filterConfig.getInitParameter("logHeaders");
        if (logHeaders1 == null || logHeaders1.isEmpty()) {
            logHeaders1 = Constant.CONTENT_TYPE;
        }
        String[] split2 = logHeaders1.split(Constant.SPLIT);
        logHeaders = Arrays.stream(split2).filter(f -> !f.trim().isEmpty()).collect(Collectors.toSet());

        formDataEncodeFlag = Integer.parseInt(filterConfig.getInitParameter("formDataEncodeFlag"));

        String s1 = filterConfig.getInitParameter("urlExcludePatterns");
        String[] split3 = s1.split(Constant.SPLIT);
        urlExcludePatterns = Arrays.stream(split3).filter(f -> !f.trim().isEmpty()).collect(Collectors.toSet());
        if (urlExcludePatterns.isEmpty()) {
            urlExcludePatterns.add("**/*.js");
            urlExcludePatterns.add("**/*.css");
            urlExcludePatterns.add("**/*.html");
            urlExcludePatterns.add("/httpRawLog/history");
        }
        LOGGER.debug("init HttpRawLogFilter complete. urls = {} ,urlExcludePatterns = {}  ,log headers = {} ,formDataEncode = {}",
                getServletContext().getFilterRegistration(Constant.FILTER_NAME).getUrlPatternMappings(),
                urlExcludePatterns, logHeaders, formDataEncodeFlag);
    }

    /**
     * 给定一个请求 URI，判断其是否在排除的URL列表中
     *
     * @param uri 给定的URI
     * @return true 在排除URL列表中，false 不在
     */
    private boolean excludeMatch(String uri) {
        final AntPathMatcher urlExcludePattern = new AntPathMatcher();
        for (String excludePattern : urlExcludePatterns) {
            if (urlExcludePattern.match(excludePattern, uri)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        // 非debug 默认不开启
        if (!LOGGER.isDebugEnabled()) {
            chain.doFilter(request, response);
            return;
        }
        String requestURI = request.getRequestURI();
        // 判断uri 是否在需要排除的URL列表中
        if (excludeMatch(requestURI)) {
            LOGGER.debug("request uri:{} in exclude urls skip it.", requestURI);
            chain.doFilter(request, response);
            return;
        }
        printLog(request, response, chain);
    }

    private void printLog(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
        // 定义变量
        String logId, method, requestURL, requestHeaders, queryStringLog, requestBody, responseBody, httpStatus, responseHeaders;
        // 写入logId
        logId = request.getHeader(Constant.LOG_ID);
        if (logId == null || logId.isEmpty()) logId = UUID.randomUUID().toString();
        response.setHeader(Constant.LOG_ID, logId);
        method = request.getMethod();
        requestURL = request.getRequestURI();
        String contentType = request.getContentType();
        Map<String, String> headMap = new HashMap<>(logHeaders.size());
        headMap.put(Constant.CONTENT_TYPE, contentType);
        for (String logHeader : logHeaders) {
            headMap.put(logHeader, request.getHeader(logHeader));
        }
        // 需要log 的request headers
        requestHeaders = headMap.toString();

        // 包装 response
        ResponseWrapper responseWrapper = new ResponseWrapper(response);
        boolean isMultipart = contentType != null && contentType.toLowerCase().startsWith(Constant.FORM_DATA);
        if (isMultipart) {
            // 是 multipart/form-data 个请求，参考：org.springframework.web.servlet.DispatcherServlet#doDispatch
            FormRequestWrapper formRequestWrapper = new FormRequestWrapper(request, formDataEncodeFlag);
            queryStringLog = RequestWrapper.queryStringLog(formRequestWrapper.getParameterMap());
            requestBody = "";
            chain.doFilter(formRequestWrapper, responseWrapper);
        } else {
            RequestWrapper requestWrapper = new RequestWrapper(request, formDataEncodeFlag);
            queryStringLog = RequestWrapper.queryStringLog(requestWrapper.getParameterMap());
            requestBody = requestWrapper.getRequestBodyLogString();
            chain.doFilter(requestWrapper, responseWrapper);
        }
        // 将response steam 重新写入返回
        byte[] bytes = responseWrapper.getByteArrayOutputStream();
        response.getOutputStream().write(bytes);
        response.getOutputStream().flush();

        // 排除resp 的流
        String responseWrapperContentType = response.getContentType();
        String header1 = response.getHeader(Constant.CONTENT_DISPOSITION);
        if (Constant.APPLICATION_OCTET_STREAM.equalsIgnoreCase(responseWrapperContentType) || header1 != null) {
            String s = "resp type : application/octet stream, so not log , response body size = " + bytes.length;
            responseBody = new String(s.getBytes());
        } else {
            String charset = Optional.ofNullable(response.getCharacterEncoding()).orElse("utf8");
            responseBody = new String(bytes, charset);
        }
        httpStatus = String.valueOf(response.getStatus());
        Map<String, String> respHeadMap = new HashMap<>(logHeaders.size());
        for (String logHeader : logHeaders) {
            String header = response.getHeader(logHeader);
            if (header != null && !header.isEmpty()) {
                respHeadMap.put(logHeader, header);
            }
        }
        responseHeaders = respHeadMap.toString();

        // 记录日志
        if (historyRecord != null) {
            historyRecord.record(new History(LocalDateTime.now().toString(), logId, method, requestURL, requestHeaders, queryStringLog,
                    requestBody, httpStatus, responseHeaders, responseBody));
        }
    }


}