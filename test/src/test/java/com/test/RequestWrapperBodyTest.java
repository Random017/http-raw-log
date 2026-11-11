package com.test;

import com.skycong.httprawlog.wrapper.RequestWrapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RequestWrapper 组件级测试：直接构造 MockHttpServletRequest 包装，验证
 * “回放给业务的 body 始终完整”与“日志字符串按 4MB 截断”的契约，不依赖 Spring 容器，运行快。
 */
public class RequestWrapperBodyTest {

    private static final int FOUR_MB = 4 * 1024 * 1024;

    /**
     * 5MB 请求体：业务回放完整 5MB；日志只保留开头 4MB 并带截断标记与真实总长度。
     */
    @Test
    void largeBody_replayedComplete_logTruncated() throws IOException {
        byte[] body = filled(FOUR_MB + 1024 * 1024, (byte) 'A');
        RequestWrapper wrapper = wrap(body, "application/json");

        // 业务回放 body 完整
        byte[] replayed = StreamUtils.copyToByteArray(wrapper.getInputStream());
        assertEquals(body.length, replayed.length);
        assertArrayEquals(body, replayed);

        // 日志按 4MB 截断，且提示里带真实原始大小
        String log = wrapper.getRequestBodyLogString();
        assertTrue(log.startsWith("A"));
        assertTrue(log.contains("[request body truncated, total size = " + body.length + " bytes"));
        assertTrue(log.length() > FOUR_MB);
    }

    /**
     * 1KB 请求体：回放完整，日志完整且无截断标记。
     */
    @Test
    void smallBody_logNotTruncated() throws IOException {
        String body = repeat('B', 1024);
        RequestWrapper wrapper = wrap(body.getBytes(StandardCharsets.US_ASCII), "application/json");

        byte[] replayed = StreamUtils.copyToByteArray(wrapper.getInputStream());
        assertEquals(body, new String(replayed, StandardCharsets.US_ASCII));

        String log = wrapper.getRequestBodyLogString();
        assertFalse(log.contains("request body truncated"));
        assertEquals(body, log);
    }

    /**
     * 边界：4MB + 1 字节，日志应被截断，但业务回放仍是 4MB + 1 字节。
     */
    @Test
    void boundary_fourMbPlusOne_logTruncated_businessComplete() throws IOException {
        byte[] body = filled(FOUR_MB + 1, (byte) 'C');
        RequestWrapper wrapper = wrap(body, "application/json");
        assertTrue(wrapper.getRequestBodyLogString().contains("[request body truncated"));
        assertEquals(body.length, StreamUtils.copyToByteArray(wrapper.getInputStream()).length);
    }

    /**
     * 边界：正好 4MB，不应被截断。
     */
    @Test
    void boundary_fourMb_notTruncated() {
        RequestWrapper wrapper = wrap(filled(FOUR_MB, (byte) 'D'), "application/json");
        assertFalse(wrapper.getRequestBodyLogString().contains("request body truncated"));
    }

    /**
     * application/x-www-form-urlencoded：URL 解码后可读，小 body 不截断。
     */
    @Test
    void formUrlEncoded_decodedAndNotTruncated() {
        String body = "name=%E5%BC%A0%E4%B8%89&age=18";
        RequestWrapper wrapper = wrap(body.getBytes(StandardCharsets.UTF_8), "application/x-www-form-urlencoded");
        String log = wrapper.getRequestBodyLogString();
        assertTrue(log.contains("张三"));
        assertFalse(log.contains("request body truncated"));
    }

    /**
     * 空请求体：日志字符串为空。
     */
    @Test
    void emptyBody_returnsEmpty() {
        RequestWrapper wrapper = wrap(new byte[0], "application/json");
        assertEquals("", wrapper.getRequestBodyLogString());
    }

    /**
     * form-urlencoded：先缓存原始 body，再解析参数，二者都不能丢。
     */
    @Test
    void formUrlEncoded_rawBodyAndParametersBothAvailable() throws IOException {
        String body = "name=%E5%BC%A0%E4%B8%89&age=18";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/x-www-form-urlencoded");
        request.setContent(body.getBytes(StandardCharsets.US_ASCII));

        RequestWrapper wrapper = new RequestWrapper(request, 0);

        assertEquals(body, new String(StreamUtils.copyToByteArray(wrapper.getInputStream()),
                StandardCharsets.US_ASCII));
        assertEquals(body.length(), wrapper.getContentLength());
        assertEquals("张三", wrapper.getParameter("name"));
        assertEquals("18", wrapper.getParameter("age"));
        String log = wrapper.getRequestBodyLogString();
        assertTrue(log.contains("name=张三"), log);
        assertTrue(log.contains("age=18"), log);
    }

    /**
     * JSON 请求没有 charset、容器又声明 ISO-8859-1 时，请求侧日志和 getReader 都应按 UTF-8 解码。
     */
    @Test
    void jsonBodyWithoutCharset_usesUtf8ForLogAndReader() throws IOException {
        String json = "{\"name\":\"中文请求\"}";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/json");
        request.setCharacterEncoding("ISO-8859-1");
        request.setContent(json.getBytes(StandardCharsets.UTF_8));

        RequestWrapper wrapper = new RequestWrapper(request, 0);
        assertEquals(json, wrapper.getRequestBodyLogString());

        String readerText;
        try (BufferedReader reader = wrapper.getReader()) {
            readerText = reader.lines().collect(Collectors.joining("\n"));
        }
        assertEquals(json, readerText);
    }

    /**
     * 业务 reader 保持声明编码：text/plain + ISO-8859-1 时不能因为字节恰好是合法 UTF-8 就改成 UTF-8。
     * 日志字符串仍使用 UTF-8 启发式。
     */
    @Test
    void textPlainIsoDeclaration_readerKeepsIsoWhileLogUsesUtf8() throws IOException {
        String text = "中文";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("text/plain");
        request.setCharacterEncoding(StandardCharsets.ISO_8859_1.name());
        request.setContent(bytes);

        RequestWrapper wrapper = new RequestWrapper(request, 0);

        String readerText;
        try (BufferedReader reader = wrapper.getReader()) {
            readerText = reader.lines().collect(Collectors.joining("\n"));
        }
        assertEquals(new String(bytes, StandardCharsets.ISO_8859_1), readerText);
        assertEquals(text, wrapper.getRequestBodyLogString());
    }

    /**
     * form body 中没有 '=' 的 flag 参数应按 Servlet 语义返回空串，而不是 null（参数缺失）。
     */
    @Test
    void formFlagWithoutEquals_returnsEmptyString() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/x-www-form-urlencoded");
        request.setContent("flag&name=%E5%BC%A0%E4%B8%89".getBytes(StandardCharsets.US_ASCII));

        RequestWrapper wrapper = new RequestWrapper(request, 0);

        assertEquals("", wrapper.getParameter("flag"));
        assertArrayEquals(new String[]{""}, wrapper.getParameterValues("flag"));
        assertEquals("张三", wrapper.getParameter("name"));
    }

    /**
     * query string 先按 & 拆分再解码：值里的 %26 不能被当成参数分隔符。
     */
    @Test
    void queryStringMap_decodesBeforeSplitting() {
        Map<String, String[]> map = RequestWrapper.getQueryStringMap("a=%26b&c=1&c=2&flag");
        assertNotNull(map.get("a"));
        assertEquals("&b", map.get("a")[0]);
        assertArrayEquals(new String[]{"1", "2"}, map.get("c"));
        assertArrayEquals(new String[]{""}, map.get("flag"));
    }

    /**
     * 包装时不能原地修改容器持有的参数数组，否则业务侧会拿到被重新编码后的值。
     */
    @Test
    void parameterMap_doesNotMutateOriginalArray() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("p", "中文");
        String[] original = request.getParameterMap().get("p");
        assertSame(original, request.getParameterMap().get("p"));

        RequestWrapper wrapper = new RequestWrapper(request, 0);

        assertEquals("中文", original[0]);
        assertTrue(wrapper.getParameter("p") != null);
    }

    private static RequestWrapper wrap(byte[] content, String contentType) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setMethod("POST");
        req.setContentType(contentType);
        req.setContent(content);
        return new RequestWrapper(req, 0);
    }

    private static byte[] filled(int n, byte b) {
        byte[] arr = new byte[n];
        for (int i = 0; i < n; i++) {
            arr[i] = b;
        }
        return arr;
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
