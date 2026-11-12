package com.skycong.httprawlog.filter;


import com.skycong.httprawlog.Constant;
import com.skycong.httprawlog.api.History;
import com.skycong.httprawlog.api.HistoryRecord;
import com.skycong.httprawlog.api.StatisticsApi;
import com.skycong.httprawlog.util.CharsetSupport;
import com.skycong.httprawlog.wrapper.BoundedRequestWrapper;
import com.skycong.httprawlog.wrapper.FormRequestWrapper;
import com.skycong.httprawlog.wrapper.RequestWrapper;
import com.skycong.httprawlog.wrapper.ResponseWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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
     * 默认请求体日志上限 64KB；已知长度超过时不再包装请求，业务读取原始流
     */
    private static final int DEFAULT_MAX_REQUEST_BODY_BYTES = 64 * 1024;

    /**
     * 默认响应日志上限 64KB；二进制/附件/压缩/SSE 直接跳过捕获
     */
    private static final int DEFAULT_MAX_RESPONSE_LOG_BYTES = 64 * 1024;

    /**
     * 需要打印的http header，包括request 和response
     */
    private Set<String> logHeaders;

    /**
     * 忽略的 url ，支持ant 路径格式
     */
    private Set<String> urlExcludePatterns;

    /**
     * 从形如 <code>&#47;**&#47;*.ext</code> 的排除模式提取扩展名，用于快速跳过静态资源
     */
    private Set<String> urlExcludeSuffixes = Collections.emptySet();

    /**
     * form-data  是否需要重新编码(0: 自动判断，1：始终需要编码，2：始终不编码)
     */
    private int formDataEncodeFlag;
    /**
     * 是否统计接口耗时
     */
    private boolean logStatistics;

    /**
     * 请求体日志上限；已知长度超过时不包装，chunked 请求由 {@link BoundedRequestWrapper} 预读上限 + 1 字节
     */
    private int maxRequestBodyBytes;

    /**
     * 响应日志上限
     */
    private int maxResponseLogBytes;

    /**
     * 默认的 HistoryRecord 实现
     */
    private final HistoryRecord historyRecord;

    /**
     * 统计实现；允许为空（统计关闭或使用方自行注册 filter 时）
     */
    private final StatisticsApi statisticsApi;

    public HttpRawLogFilter(HistoryRecord historyRecord) {
        this(historyRecord, null);
    }

    public HttpRawLogFilter(HistoryRecord historyRecord, StatisticsApi statisticsApi) {
        this.historyRecord = historyRecord;
        this.statisticsApi = statisticsApi;
    }

    @Override
    protected void initFilterBean() {
        FilterConfig filterConfig = getFilterConfig();
        if (filterConfig == null) {
            logHeaders = defaultLogHeaders();
            urlExcludePatterns = defaultUrlExcludePatterns();
            urlExcludeSuffixes = buildExcludeSuffixes(urlExcludePatterns);
            formDataEncodeFlag = 0;
            logStatistics = true;
            maxRequestBodyBytes = DEFAULT_MAX_REQUEST_BODY_BYTES;
            maxResponseLogBytes = DEFAULT_MAX_RESPONSE_LOG_BYTES;
            return;
        }

        String logHeadersConfig = filterConfig.getInitParameter("logHeaders");
        if (logHeadersConfig == null || logHeadersConfig.trim().isEmpty()) {
            logHeadersConfig = Constant.CONTENT_TYPE;
        }
        logHeaders = Arrays.stream(logHeadersConfig.split(Constant.SPLIT))
                .map(String::trim)
                .filter(header -> !header.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        formDataEncodeFlag = parseInteger(filterConfig.getInitParameter("formDataEncodeFlag"), 0, "formDataEncodeFlag");

        urlExcludePatterns = Arrays.stream(option(filterConfig.getInitParameter("urlExcludePatterns")).split(Constant.SPLIT))
                .map(String::trim)
                .filter(pattern -> !pattern.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
        urlExcludePatterns.addAll(Constant.DEFAULT_URL_EXCLUDE_PATTERNS);
        urlExcludeSuffixes = buildExcludeSuffixes(urlExcludePatterns);

        logStatistics = parseBoolean(filterConfig.getInitParameter("logStatistics"), true);

        maxRequestBodyBytes = positiveInt(filterConfig.getInitParameter("maxRequestBodyBytes"),
                DEFAULT_MAX_REQUEST_BODY_BYTES, "maxRequestBodyBytes");
        maxResponseLogBytes = positiveInt(filterConfig.getInitParameter("maxResponseLogBytes"),
                DEFAULT_MAX_RESPONSE_LOG_BYTES, "maxResponseLogBytes");

        LOGGER.debug("init HttpRawLogFilter complete. urlExcludePatterns = {} ,log headers = {} ,formDataEncode = {} ,logStatistics = {} ,maxRequestBodyBytes = {} ,maxResponseLogBytes = {}",
                urlExcludePatterns, logHeaders, formDataEncodeFlag, logStatistics, maxRequestBodyBytes, maxResponseLogBytes);
    }

    private static Set<String> defaultLogHeaders() {
        return Arrays.stream(Constant.CONTENT_TYPE.split(Constant.SPLIT))
                .map(String::trim)
                .filter(header -> !header.isEmpty())
                .collect(Collectors.toSet());
    }

    private static Set<String> defaultUrlExcludePatterns() {
        return new HashSet<>(Constant.DEFAULT_URL_EXCLUDE_PATTERNS);
    }

    /**
     * 提取只按扩展名匹配的排除模式，例如 <code>&#47;**&#47;*.html</code> 提取为 {@code .html}。
     * 仅作为 {@link #excludeMatch(String)} 的快速路径，未命中的 URI 仍走 AntPathMatcher。
     */
    private static Set<String> buildExcludeSuffixes(Set<String> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> suffixes = new HashSet<>();
        for (String pattern : patterns) {
            if (pattern != null
                    && pattern.startsWith("/**/*.")
                    && pattern.indexOf('*', 5) < 0
                    && pattern.indexOf('?', 5) < 0) {
                suffixes.add(pattern.substring(5));
            }
        }
        return suffixes;
    }

    private static String option(String value) {
        return value == null ? "" : value;
    }

    private static int parseInteger(String value, int defaultValue, String name) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("invalid filter init-param {} = {}, fallback to {}", name, value, defaultValue);
            return defaultValue;
        }
    }

    private static int positiveInt(String value, int defaultValue, String name) {
        int parsed = parseInteger(value, defaultValue, name);
        return parsed > 0 ? parsed : defaultValue;
    }

    private static boolean parseBoolean(String value, boolean defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
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
        printLog(request, response, chain, requestURI);
    }

    private void printLog(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
                          String requestURL) throws IOException, ServletException {
        long st = System.currentTimeMillis();
        // 定义变量
        String logId, method, requestHeaders, queryStringLog, requestBody;
        // 写入logId
        logId = request.getHeader(Constant.LOG_ID);
        if (logId == null || logId.isEmpty()) {
            logId = UUID.randomUUID().toString();
        }
        response.setHeader(Constant.LOG_ID, logId);
        method = request.getMethod();
        String contentType = request.getContentType();

        // 需要log 的request headers；LinkedHashMap 保证顺序，跳过 null/空值
        Map<String, String> headMap = new LinkedHashMap<>();
        putIfPresent(headMap, Constant.CONTENT_TYPE, request.getHeader(Constant.CONTENT_TYPE));
        for (String logHeader : logHeaders) {
            putIfPresent(headMap, logHeader, request.getHeader(logHeader));
        }
        requestHeaders = headMap.toString();

        // 包装 response：业务写入直接透传客户端，同时旁路复制日志
        ResponseWrapper responseWrapper = new ResponseWrapper(response, maxResponseLogBytes);
        boolean isMultipart = isMultipartContentType(contentType);
        ServletRequest requestToUse;
        if (!mayHaveRequestBody(method)) {
            // GET/HEAD 等本来就没有 body，直接记录空 body，不能写成 skipped 污染历史
            queryStringLog = RequestWrapper.queryStringLog(RequestWrapper.getQueryStringMap(request.getQueryString()));
            requestBody = "";
            requestToUse = request;
        } else {
            long contentLength = declaredContentLength(request);
            if (isMultipart) {
                // 大文件/未知长度 multipart 不解析，避免 FormRequestWrapper 把上传流读进内存
                String skipReason = requestBodySkipReason(request, contentType, contentLength);
                if (skipReason != null) {
                    queryStringLog = RequestWrapper.queryStringLog(RequestWrapper.getQueryStringMap(request.getQueryString()));
                    requestBody = "[request body skipped: " + skipReason + "]";
                    requestToUse = request;
                } else {
                    // 是 multipart/form-data 个请求，参考：org.springframework.web.servlet.DispatcherServlet#doDispatch
                    FormRequestWrapper formRequestWrapper = new FormRequestWrapper(request, formDataEncodeFlag);
                    queryStringLog = RequestWrapper.queryStringLog(formRequestWrapper.getParameterMap());
                    requestBody = "";
                    requestToUse = formRequestWrapper;
                }
            } else {
                String skipReason = requestBodySkipReason(request, contentType, contentLength);
                if (skipReason != null) {
                    // 不包装请求：业务拿原始流，避免大 body / 二进制 body 占满内存
                    queryStringLog = RequestWrapper.queryStringLog(RequestWrapper.getQueryStringMap(request.getQueryString()));
                    requestBody = "[request body skipped: " + skipReason + "]";
                    requestToUse = request;
                } else if (contentLength == 0) {
                    // 明确声明没有 body：无需包装，避免为每个空 POST 解析一次参数
                    queryStringLog = RequestWrapper.queryStringLog(RequestWrapper.getQueryStringMap(request.getQueryString()));
                    requestBody = "";
                    requestToUse = request;
                } else if (contentLength < 0) {
                    // chunked/未知长度：最多预读 maxRequestBodyBytes+1 字节，剩余部分继续回放给业务
                    BoundedRequestWrapper boundedRequest = new BoundedRequestWrapper(request, maxRequestBodyBytes);
                    queryStringLog = RequestWrapper.queryStringLog(boundedRequest.getParameterMap());
                    requestBody = boundedRequest.getRequestBodyLogString();
                    requestToUse = boundedRequest;
                } else {
                    RequestWrapper requestWrapper = new RequestWrapper(request, formDataEncodeFlag);
                    queryStringLog = RequestWrapper.queryStringLog(requestWrapper.getParameterMap());
                    requestBody = requestWrapper.getRequestBodyLogString();
                    requestToUse = requestWrapper;
                }
            }
        }

        Throwable failure = null;
        try {
            chain.doFilter(requestToUse, responseWrapper);
        } catch (IOException | ServletException | RuntimeException e) {
            failure = e;
            throw e;
        } catch (Error e) {
            failure = e;
            throw e;
        } finally {
            // 异步请求在此刻还没有真正结束：注册 AsyncListener，等 onComplete/onError/onTimeout 再记录
            if (failure == null && isAsyncStarted(request) && registerAsyncListener(request, response, responseWrapper,
                    logId, method, requestURL, requestHeaders, queryStringLog, requestBody, st)) {
                LOGGER.debug("async request hLogId={} registered, waiting for completion", logId);
            } else {
                recordLog(response, responseWrapper, logId, method, requestURL, requestHeaders,
                        queryStringLog, requestBody, st, failure);
            }
        }
    }

    /**
     * 是否可能带请求体。GET/HEAD/OPTIONS/TRACE 通常没有 body，
     * 不包装也不写 skipped 标记，避免污染历史记录。
     */
    private static boolean mayHaveRequestBody(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }

    private static boolean isFormUrlEncoded(String contentType) {
        return contentType != null && Constant.FORM_URLENCODED.equals(mediaType(contentType));
    }

    private static boolean isMultipartContentType(String contentType) {
        return contentType != null && Constant.FORM_DATA.equals(mediaType(contentType));
    }

    private static String mediaType(String contentType) {
        int index = contentType.indexOf(';');
        String type = index >= 0 ? contentType.substring(0, index) : contentType;
        return type.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 请求体是否跳过捕获：大 body、二进制 body、压缩 body、form/multipart 未知长度都不包装请求；
     * 普通 chunked 请求由 {@link BoundedRequestWrapper} 做有界预读。
     *
     * @param request       当前请求
     * @param contentType   Content-Type
     * @param contentLength 已解析的 Content-Length，-1 表示未知
     * @return 跳过原因；返回 null 表示可以安全包装
     */
    private String requestBodySkipReason(HttpServletRequest request, String contentType, long contentLength) {
        String contentEncoding = request.getHeader("Content-Encoding");
        if (contentEncoding != null && !contentEncoding.isEmpty()
                && !"identity".equalsIgnoreCase(contentEncoding.trim())) {
            return "content-encoding=" + contentEncoding;
        }
        if (isBinaryContentType(contentType)) {
            return "content-type=" + contentType;
        }
        if (contentLength < 0) {
            // form 需要完整 body 解析参数，multipart 需要完整 part；都保持原来的跳过策略。
            // 其他类型交给 BoundedRequestWrapper 预读并回放。
            return isFormUrlEncoded(contentType) || isMultipartContentType(contentType)
                    ? "unknown content-length"
                    : null;
        }
        if (contentLength > maxRequestBodyBytes) {
            return "content-length=" + contentLength + " > maxRequestBodyBytes=" + maxRequestBodyBytes;
        }
        return null;
    }

    /**
     * 优先使用容器解析的 Content-Length；再取原始头兜底，防止容器/测试实现给出 0 或 -1。
     */
    private static long declaredContentLength(HttpServletRequest request) {
        long contentLength = request.getContentLengthLong();
        String header = request.getHeader("Content-Length");
        if (header != null && !header.trim().isEmpty()) {
            try {
                long declared = Long.parseLong(header.trim());
                if (declared >= 0) {
                    contentLength = Math.max(contentLength, declared);
                }
            } catch (NumberFormatException ignored) {
                // 非法头交给容器处理
            }
        }
        return contentLength;
    }

    private static boolean isBinaryContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        // multipart 的 boundary 可能很长，先按前缀排除，避免为它复制整串小写
        if (contentType.regionMatches(true, 0, "multipart/", 0, 10)) {
            return false;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        return ct.startsWith("image/")
                || ct.startsWith("audio/")
                || ct.startsWith("video/")
                || ct.startsWith("font/")
                || ct.startsWith("application/octet-stream")
                || ct.startsWith("application/pdf")
                || ct.startsWith("application/zip")
                || ct.startsWith("application/gzip")
                || ct.startsWith("application/x-gzip")
                || ct.startsWith("application/x-rar")
                || ct.startsWith("application/x-7z")
                || ct.startsWith("application/wasm")
                || ct.startsWith("application/x-protobuf")
                || ct.startsWith("application/protobuf")
                || ct.startsWith("application/msgpack")
                || ct.startsWith("application/cbor");
    }

    /**
     * 注册异步监听器；如果异步已经结束则返回 false，由调用方立即记录。
     */
    private boolean registerAsyncListener(HttpServletRequest request, HttpServletResponse response, ResponseWrapper responseWrapper,
                                          String logId, String method, String requestURL, String requestHeaders,
                                          String queryStringLog, String requestBody, long st) {
        try {
            request.getAsyncContext().addListener(new AsyncLogListener(response, responseWrapper, logId, method, requestURL,
                    requestHeaders, queryStringLog, requestBody, st));
            return true;
        } catch (IllegalStateException e) {
            LOGGER.debug("async request already completed before listener registration, record immediately", e);
            return false;
        }
    }

    /**
     * 异步请求在真正结束时记录；onComplete/onError/onTimeout 只记录一次。
     */
    private final class AsyncLogListener implements AsyncListener {

        private final HttpServletResponse response;
        private final ResponseWrapper responseWrapper;
        private final String logId;
        private final String method;
        private final String requestURL;
        private final String requestHeaders;
        private final String queryStringLog;
        private final String requestBody;
        private final long st;
        private final AtomicBoolean recorded = new AtomicBoolean(false);

        private AsyncLogListener(HttpServletResponse response, ResponseWrapper responseWrapper,
                                 String logId, String method, String requestURL, String requestHeaders,
                                 String queryStringLog, String requestBody, long st) {
            this.response = response;
            this.responseWrapper = responseWrapper;
            this.logId = logId;
            this.method = method;
            this.requestURL = requestURL;
            this.requestHeaders = requestHeaders;
            this.queryStringLog = queryStringLog;
            this.requestBody = requestBody;
            this.st = st;
        }

        @Override
        public void onComplete(AsyncEvent event) {
            record(null);
        }

        @Override
        public void onTimeout(AsyncEvent event) {
            record(new TimeoutException("async request timeout"));
        }

        @Override
        public void onError(AsyncEvent event) {
            record(event.getThrowable());
        }

        @Override
        public void onStartAsync(AsyncEvent event) {
            // 不重复注册
        }

        private void record(Throwable failure) {
            if (!recorded.compareAndSet(false, true)) {
                return;
            }
            recordLog(response, responseWrapper, logId, method, requestURL, requestHeaders,
                    queryStringLog, requestBody, st, failure);
        }
    }

    private void recordLog(HttpServletResponse response, ResponseWrapper responseWrapper, String logId, String method,
                           String requestURL, String requestHeaders, String queryStringLog, String requestBody,
                           long st, Throwable failure) {
        try {
            // writer 中的字符可能仍在缓冲区（视图渲染/异步写入）
            try {
                responseWrapper.flushWriter();
            } catch (Throwable t) {
                LOGGER.debug("flush response writer failed", t);
            }

            long cost = System.currentTimeMillis() - st;
            if (logStatistics && statisticsApi != null) {
                // 异步统计耗时
                statisticsApi.record(requestURL, (int) cost);
            }

            byte[] captured = responseWrapper.getCapturedBuffer();
            int len = responseWrapper.getCapturedLength();
            String contentType = response.getContentType();
            String responseBody;
            if (responseWrapper.isCaptureSkipped()) {
                responseBody = "response content-type:" + contentType + ", so not log, reason: "
                        + responseWrapper.getCaptureSkipReason() + ", response body size is "
                        + responseWrapper.getTotalBytesWritten();
            } else if (isTextContent(captured, len, contentType)) {
                responseBody = new String(captured, 0, len,
                        CharsetSupport.resolve(contentType, response.getCharacterEncoding(), captured, len));
                if (responseWrapper.isTruncated()) {
                    responseBody += "\n... [response body truncated, only first "
                            + responseWrapper.getMaxLogBodySize() + " bytes logged]";
                }
            } else {
                responseBody = "response content-type:" + contentType + ", so not log, response body size is "
                        + responseWrapper.getTotalBytesWritten();
            }

            // sendError 时容器渲染的错误页不在本 filter 的 dispatch 中，至少把状态/消息写入日志
            if (responseWrapper.isErrorSent()) {
                String errorSummary = responseWrapper.getErrorSummary();
                responseBody = responseBody == null || responseBody.isEmpty()
                        ? errorSummary
                        : responseBody + "\n" + errorSummary;
            }

            int status = response.getStatus();
            String httpStatus = String.valueOf(status);
            if (failure != null) {
                // 异常仍在向上抛时 response.getStatus() 还是 200，这里按 500 记录
                if (status < 400) {
                    httpStatus = "500";
                }
                responseBody = appendException(responseBody, failure);
            }

            Map<String, String> respHeadMap = new LinkedHashMap<>();
            for (String logHeader : logHeaders) {
                putIfPresent(respHeadMap, logHeader, responseHeader(response, logHeader));
            }
            String responseHeaders = respHeadMap.toString();

            // 记录日志
            if (historyRecord != null) {
                historyRecord.record(new History(LocalDateTime.now().toString(), logId, method, requestURL, requestHeaders, queryStringLog,
                        requestBody, httpStatus, responseHeaders, responseBody, cost));
            }
        } catch (Throwable t) {
            // 日志记录失败不能影响业务响应
            LOGGER.warn("record http raw log failed: {}", t.toString());
        }
    }

    private static String appendException(String responseBody, Throwable failure) {
        // Servlet 容器通常会把 Controller 异常包一层 ServletException，日志取最内层原因更有用
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message != null && message.length() > 512) {
            message = message.substring(0, 512) + "...";
        }
        String summary = "[exception: " + root.getClass().getName()
                + (message == null || message.isEmpty() ? "" : ": " + message) + "]";
        return responseBody == null || responseBody.isEmpty() ? summary : responseBody + "\n" + summary;
    }

    private static void putIfPresent(Map<String, String> map, String name, String value) {
        if (name != null && !name.isEmpty() && value != null && !value.isEmpty()) {
            map.put(name, value);
        }
    }

    /**
     * Tomcat 通过 setContentType 设置的媒体类型不一定能通过 getHeader("content-type") 取到，
     * 因此在 header 为空时回退到 getContentType()。
     */
    private static String responseHeader(HttpServletResponse response, String name) {
        String value = response.getHeader(name);
        if ((value == null || value.isEmpty()) && Constant.CONTENT_TYPE.equalsIgnoreCase(name)) {
            value = response.getContentType();
        }
        return value;
    }


    /**
     * 给定一个请求 URI，判断其是否在排除的URL列表中
     *
     * @param uri 给定的URI
     * @return true 在排除URL列表中，false 不在
     */
    private boolean excludeMatch(String uri) {
        if (!urlExcludeSuffixes.isEmpty()) {
            int lastSlash = uri.lastIndexOf('/');
            int lastDot = uri.lastIndexOf('.');
            if (lastDot > lastSlash && urlExcludeSuffixes.contains(uri.substring(lastDot))) {
                return true;
            }
        }
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

    /**
     * 在 {@link #isTextContent(byte[], int)} 基础上，按 Content-Type 快速排除明确的二进制响应，
     * 避免把文件下载内容误当文本写入日志。
     */
    static boolean isTextContent(byte[] bytes, int len, String contentType) {
        if (contentType != null) {
            String ct = contentType.toLowerCase(Locale.ROOT);
            if (ct.startsWith("image/")
                    || ct.startsWith("audio/")
                    || ct.startsWith("video/")
                    || ct.startsWith("font/")
                    || ct.startsWith("application/octet-stream")
                    || ct.startsWith("application/pdf")
                    || ct.startsWith("application/zip")
                    || ct.startsWith("application/gzip")
                    || ct.startsWith("application/x-gzip")) {
                return false;
            }
        }
        return isTextContent(bytes, len);
    }
}
