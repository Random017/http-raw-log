package com.test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.skycong.httprawlog.api.History;
import com.skycong.httprawlog.api.HistoryApi;
import com.skycong.httprawlog.api.HistoryStore;
import com.skycong.httprawlog.filter.HttpRawLogFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HttpRawLogFilter 请求体防护的组件级测试：不经过 MVC 消息转换器，直接验证 filter 的跳过逻辑。
 */
public class HttpRawLogFilterTest {

    private final Logger filterLogger = (Logger) LoggerFactory.getLogger(HttpRawLogFilter.class);
    private Level originalLevel;
    private HistoryStore store;
    private HttpRawLogFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        // filter 只在 debug 打开时工作
        originalLevel = filterLogger.getLevel();
        filterLogger.setLevel(Level.DEBUG);
        store = new HistoryStore(10, 1024 * 1024);
        filter = new HttpRawLogFilter(new HistoryApi(store, 0, 32 * 1024));
        filter.init(new MockFilterConfig());
    }

    @AfterEach
    void tearDown() {
        filterLogger.setLevel(originalLevel);
    }

    @Test
    void declaredHugeContentLength_isSkippedWithoutAllocation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/echo");
        request.setContentType("application/json");
        request.setContent(new byte[0]);
        request.addHeader("Content-Length", String.valueOf(Integer.MAX_VALUE));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        History history = store.snapshot().get(0);
        assertTrue(history.getRequestBody().startsWith("[request body skipped: content-length=2147483647"),
                "unexpected: " + history.getRequestBody());
        assertTrue(history.getRequestBody().contains("maxRequestBodyBytes=65536"));
    }

    @Test
    void binaryContentType_isSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/upload");
        request.setContentType("application/octet-stream");
        request.setContent(new byte[4096]);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        History history = store.snapshot().get(0);
        assertTrue(history.getRequestBody().startsWith("[request body skipped: content-type=application/octet-stream"),
                "unexpected: " + history.getRequestBody());
    }

    @Test
    void compressedRequest_isSkipped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/echo");
        request.setContentType("application/json");
        request.addHeader("Content-Encoding", "gzip");
        request.setContent("{}".getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        History history = store.snapshot().get(0);
        assertTrue(history.getRequestBody().startsWith("[request body skipped: content-encoding=gzip"),
                "unexpected: " + history.getRequestBody());
    }

    @Test
    void largeMultipart_isSkippedBeforeParsing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/upload");
        request.setContentType("multipart/form-data; boundary=test");
        request.addHeader("Content-Length", String.valueOf(1024 * 1024));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        History history = store.snapshot().get(0);
        assertTrue(history.getRequestBody().startsWith("[request body skipped: content-length=1048576"),
                "unexpected: " + history.getRequestBody());
    }

    @Test
    void emptyBody_isNotWrapped() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/echo");
        request.setContentType("application/json");
        request.setContent(new byte[0]);
        request.addHeader("Content-Length", "0");
        AtomicReference<ServletRequest> passedRequest = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(),
                (req, res) -> passedRequest.set(req));

        assertSame(request, passedRequest.get(), "empty body should not allocate a request wrapper");
        History history = store.snapshot().get(0);
        assertEquals("", history.getRequestBody());
    }

    @Test
    void smallJsonBody_isWrappedAndLogged() throws Exception {
        String body = "{\"name\":\"value\"}";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/test/echo");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        History history = store.snapshot().get(0);
        assertEquals(body, history.getRequestBody());
    }
}
