package com.skycong.httprawlog.filter;


import com.skycong.httprawlog.Constant;
import com.skycong.httprawlog.api.History;
import com.skycong.httprawlog.api.HistoryRecord;
import com.skycong.httprawlog.api.StatisticsApi;
import com.skycong.httprawlog.wrapper.FormRequestWrapper;
import com.skycong.httprawlog.wrapper.RequestWrapper;
import com.skycong.httprawlog.wrapper.ResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 控制台打印日志示例：
 *
 * <pre>
 * --------------> http raw data hLogId:d8016345-d980-4503-a716-ae5f3b9d71e7
 * [url]:POST	/test/post
 * [request headers]:{content-type=application/json}
 * [request body]:{
 *   "s1": "哎哎哎",
 *   "int2": 30,
 *   "adbc": true
 * }
 * [response headers]:status=200, {content-type=text/plain;charset=UTF-8}
 * [response body]:OK:TestController.Pojo(s1=哎哎哎, int2=30, adbc=true)
 * </pre>
 *
 * @author ruanmingcong
 * @version 1.0
 * @since 2020/7/1 16:54
 */
public class HttpRawLogFilter extends OncePerRequestFilter {

    public static final Logger LOGGER = LoggerFactory.getLogger(HttpRawLogFilter.class);

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

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
    /**
     * 是否统计接口耗时
     */
    private boolean logStatistics;

    /**
     * 默认的 HistoryRecord 实现
     */
    private final HistoryRecord historyRecord;

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
        urlExcludePatterns.addAll(Constant.DEFAULT_URL_EXCLUDE_PATTERNS);

        logStatistics = Boolean.parseBoolean(filterConfig.getInitParameter("logStatistics"));

        LOGGER.debug("init HttpRawLogFilter complete. urls = {} ,urlExcludePatterns = {}  ,log headers = {} ,formDataEncode = {} ,logStatistics = {}",
                getServletContext().getFilterRegistration(Constant.FILTER_NAME).getUrlPatternMappings(),
                urlExcludePatterns, logHeaders, formDataEncodeFlag, logStatistics);
    }


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        // 非debug 默认不开启 且必须有 historyRecord 实例
        if (!LOGGER.isDebugEnabled() || historyRecord == null) {
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
        long st = System.currentTimeMillis();
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
        // 直接引用 ResponseWrapper 的 buffer，零拷贝写回客户端
        byte[] buffer = responseWrapper.getBuffer();
        int len = responseWrapper.getWritePos();
        response.getOutputStream().write(buffer, 0, len);
        response.getOutputStream().flush();
        long et = System.currentTimeMillis();
        if (logStatistics) {
            // 异步统计耗时
            StatisticsApi.statistics(requestURL, (int) (et - st));
        }

        // 日志侧：直接使用 buffer（零拷贝），按需截断
        if (isTextContent(buffer, len)) {
            responseBody = new String(buffer, 0, len, StandardCharsets.UTF_8);
            if (responseWrapper.isOverflow()) {
                responseBody += "\n... [response body truncated, only first " + ResponseWrapper.MAX_LOG_BODY_SIZE + " bytes logged]";
            }
        } else {
            String ct = response.getContentType();
            responseBody = "response content-type:" + ct + ", so not log, response body size is " + len;
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
        long cost = et - st;
        historyRecord.record(new History(LocalDateTime.now().toString(), logId, method, requestURL, requestHeaders, queryStringLog,
                requestBody, httpStatus, responseHeaders, responseBody, cost));
    }


    /**
     * 给定一个请求 URI，判断其是否在排除的URL列表中
     *
     * @param uri 给定的URI
     * @return true 在排除URL列表中，false 不在
     */
    private boolean excludeMatch(String uri) {
        for (String excludePattern : urlExcludePatterns) {
            if (PATH_MATCHER.match(excludePattern, uri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断字节数组内容是否为可读文本。
     * 检测前 {@code len} 字节中是否包含 null 字节（\0），包含则视为二进制内容。
     * 空数组视为文本（空响应体）。
     *
     * @param bytes 字节数组
     * @param len   有效字节数
     * @return true 是文本内容，false 是二进制内容
     */
    static boolean isTextContent(byte[] bytes, int len) {
        if (bytes == null || len == 0) {
            return true;
        }
        int limit = Math.min(len, 128);
        for (int i = 0; i < limit; i++) {
            if (bytes[i] == 0) {
                return false;
            }
        }
        return true;
    }
}