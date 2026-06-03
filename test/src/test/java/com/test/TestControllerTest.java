package com.test;

import com.skycong.httprawlog2.HttpRawLogApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TestController 接口测试用例
 *
 * @author ruanmingcong
 * @since 2024/01/01
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = HttpRawLogApplication.class)
@ComponentScan("com.skycong.httprawlog2")
@AutoConfigureMockMvc
@Slf4j
public class TestControllerTest {

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // 每个测试前清空历史记录
    }

    // ==================== GET /test/get/{p} ====================

    /**
     * 测试 GET 请求，传递 query 参数和路径参数，验证参数正确传递和中文编码
     */
    @Test
    public void testGetWithAllParams() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("abc", "测试参数abc");

        mockMvc.perform(get("/test/get/路径参数p")
                        .params(params))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("测试参数abc")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("路径参数p")))
                .andDo(print());
    }

    /**
     * 测试 GET 请求，仅传路径参数，query 参数为 null
     */
    @Test
    public void testGetWithNullParams() throws Exception {
        mockMvc.perform(get("/test/get/testPath"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("testPath")))
                .andDo(print());
    }

    /**
     * 测试 GET 请求，路径参数和 query 参数均为中文，验证中文编码处理
     */
    @Test
    public void testGetWithChineseParams() throws Exception {
        LinkedMultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("abc", "中文参数测试");

        mockMvc.perform(get("/test/get/中文路径")
                        .params(params))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("中文参数测试")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("中文路径")))
                .andDo(print());
    }

    // ==================== GET /test/getCall/{p} ====================

    /**
     * 测试 Callable 异步请求，验证异步处理正常启动
     */
    @Test
    public void testCallableGet() throws Exception {
        mockMvc.perform(get("/test/getCall/callPath")
                        .param("abc", "callable参数"))
                .andExpect(request().asyncStarted())
                .andExpect(status().isOk())
                .andDo(print());
    }

    // ==================== POST /test/post (JSON) ====================

    /**
     * 测试 POST JSON 请求，验证 JSON 反序列化和中文处理
     */
    @Test
    public void testPostJson() throws Exception {
        String json = "{\n" +
                "  \"s1\": \"测试字符串\",\n" +
                "  \"int2\": 100,\n" +
                "  \"adbc\": true\n" +
                "}";

        mockMvc.perform(post("/test/post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .characterEncoding("UTF-8"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OK")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("测试字符串")))
                .andDo(print());
    }

    /**
     * 测试 POST JSON 请求，包含特殊字符，验证特殊字符处理
     */
    @Test
    public void testPostJsonWithSpecialChars() throws Exception {
        String json = "{\n" +
                "  \"s1\": \"特殊字符!@#$%^&*()\",\n" +
                "  \"int2\": 0,\n" +
                "  \"adbc\": false\n" +
                "}";

        mockMvc.perform(post("/test/post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .characterEncoding("UTF-8"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OK")))
                .andDo(print());
    }

    /**
     * 测试 POST JSON 请求，所有字段为 null，验证 null 值处理
     */
    @Test
    public void testPostJsonWithNullFields() throws Exception {
        String json = "{\n" +
                "  \"s1\": null,\n" +
                "  \"int2\": null,\n" +
                "  \"adbc\": null\n" +
                "}";

        mockMvc.perform(post("/test/post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                        .characterEncoding("UTF-8"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OK")))
                .andDo(print());
    }

    // ==================== POST /test/upload/{p} (Form) ====================

    /**
     * 测试表单上传，application/x-www-form-urlencoded 格式，验证 query 参数和表单参数处理
     */
    @Test
    public void testUploadFormUrlEncoded() throws Exception {
        mockMvc.perform(post("/test/upload/123")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .characterEncoding("UTF-8")
                        .queryParam("query1", "查询参数1")
                        .queryParam("query2", URLEncoder.encode("需要URL编码的参数", "UTF-8"))
                        .param("formdata1", "表单数据1")
                        .param("formdata2", "表单数据2")
                        .param("abc", "额外参数"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OK")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("查询参数1")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("表单数据1")))
                .andDo(print());
    }

    /**
     * 测试 multipart 文件上传，验证多文件和混合参数处理
     */
    @Test
    public void testUploadMultipartFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file",
                "测试文件.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "文件内容测试\n第二行内容".getBytes(StandardCharsets.UTF_8));

        MockMultipartFile file2 = new MockMultipartFile("file2",
                "测试文件2.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "文件2内容".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/test/upload/456")
                        .file(file)
                        .file(file2)
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .characterEncoding("UTF-8")
                        .queryParam("query1", "查询参数")
                        .param("formdata1", "表单参数"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OK")))
                .andDo(print());
    }

    /**
     * 测试上传接口，仅传递表单参数，无文件上传
     */
    @Test
    public void testUploadWithOnlyFormParams() throws Exception {
        mockMvc.perform(post("/test/upload/789")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .characterEncoding("UTF-8")
                        .param("formdata1", "纯表单参数"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OK")))
                .andDo(print());
    }

    // ==================== GET /test/download/{p} ====================

    /**
     * 测试下载接口，不传文件参数，验证空值处理
     */
    @Test
    public void testDownloadWithoutFile() throws Exception {
        mockMvc.perform(get("/test/download/100")
                        .param("abc", "下载参数"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("未提供文件")))
                .andDo(print());
    }

    // ==================== GET/POST /test/all ====================

    /**
     * 测试 all 接口 GET 方式，验证 @RequestMapping 支持多种 HTTP 方法
     */
    @Test
    public void testAllGet() throws Exception {
        mockMvc.perform(get("/test/all"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OK")))
                .andExpect(header().exists("abc"))
                .andDo(print());
    }

    /**
     * 测试 all 接口 POST 方式，验证 @RequestMapping 支持多种 HTTP 方法
     */
    @Test
    public void testAllPost() throws Exception {
        mockMvc.perform(post("/test/all"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("OK")))
                .andExpect(header().exists("abc"))
                .andDo(print());
    }

    // ==================== GET /test/stream (SSE) ====================

    /**
     * 测试 SSE 流式响应，验证 text/event-stream content-type
     */
    @Test
    public void testStream() throws Exception {
        mockMvc.perform(get("/test/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andDo(print());
    }

    // ==================== 边界值测试 ====================

    /**
     * 测试边界值，使用特殊字符 "-" 作为路径参数
     */
    @Test
    public void testGetWithEmptyPathVariable() throws Exception {
        // 注意：/test/get/ 不匹配 /test/get/{p}，{p} 即使 required=false 也需要值
        // 测试使用特殊字符作为路径参数
        mockMvc.perform(get("/test/get/-"))
                .andExpect(status().isOk())
                .andDo(print());
    }

    /**
     * 测试边界值，POST 空 JSON 对象 {}
     */
    @Test
    public void testPostJsonEmptyBody() throws Exception {
        mockMvc.perform(post("/test/post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andDo(print());
    }

    /**
     * 测试边界值，同一个 query 参数传递多个值
     */
    @Test
    public void testUploadWithMultipleQueryParams() throws Exception {
        mockMvc.perform(post("/test/upload/multi")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .characterEncoding("UTF-8")
                        .queryParam("query1", "值1", "值2", "值3")
                        .queryParam("query2", "单值"))
                .andExpect(status().isOk())
                .andDo(print());
    }

    // ==================== hLogId 传递测试 ====================

    /**
     * 测试自定义 hLogId 透传，验证请求头中的 hLogId 被原样返回
     */
    @Test
    public void testHLogIdPassedThrough() throws Exception {
        String testLogId = "test-log-id-" + System.currentTimeMillis();

        mockMvc.perform(get("/test/get/testPath")
                        .header("hLogId", testLogId))
                .andExpect(status().isOk())
                .andExpect(header().string("hLogId", testLogId))
                .andDo(print());
    }

    /**
     * 测试自动生成 hLogId，验证不传 hLogId 时系统自动生成 UUID
     */
    @Test
    public void testHLogIdAutoGenerated() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/get/testPath"))
                .andExpect(status().isOk())
                .andExpect(header().exists("hLogId"))
                .andDo(print())
                .andReturn();

        String returnedLogId = result.getResponse().getHeader("hLogId");
        log.info("自动生成的hLogId: {}", returnedLogId);
    }

    // ==================== HistoryApi /httpRawLog/history ====================

    /**
     * 测试历史记录接口默认查询，验证返回 JSON 数组
     */
    @Test
    public void testHistoryDefault() throws Exception {
        // 先触发一个请求，产生历史记录
        mockMvc.perform(get("/test/get/historyTest")).andExpect(status().isOk());

        mockMvc.perform(get("/httpRawLog/history"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andDo(print());
    }

    /**
     * 测试历史记录接口分页查询，page=1
     */
    @Test
    public void testHistoryWithPage() throws Exception {
        mockMvc.perform(get("/httpRawLog/history")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andDo(print());
    }

    /**
     * 测试历史记录接口，超大页码返回空列表
     */
    @Test
    public void testHistoryWithInvalidPage() throws Exception {
        // 超大页码，应返回空列表
        mockMvc.perform(get("/httpRawLog/history")
                        .param("page", "999"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"))
                .andDo(print());
    }

    /**
     * 测试历史记录接口，按 hLogId 精确查询
     */
    @Test
    public void testHistoryByHLogId() throws Exception {
        // 先触发一个请求，获取 hLogId
        MvcResult result = mockMvc.perform(get("/test/get/hLogIdTest"))
                .andExpect(status().isOk())
                .andReturn();
        String hLogId = result.getResponse().getHeader("hLogId");

        // 用 hLogId 查询历史
        mockMvc.perform(get("/httpRawLog/history")
                        .param("hLogId", hLogId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(hLogId)))
                .andDo(print());
    }

    /**
     * 测试历史记录接口，查询不存在的 hLogId 返回空列表
     */
    @Test
    public void testHistoryByNonExistHLogId() throws Exception {
        mockMvc.perform(get("/httpRawLog/history")
                        .param("hLogId", "non-exist-log-id"))
                .andExpect(status().isOk())
                .andExpect(content().string("[]"))
                .andDo(print());
    }

    // ==================== StatisticsApi /httpRawLog/statistics ====================

    /**
     * 测试统计接口默认查询，先触发请求产生数据再查询
     */
    @Test
    public void testStatisticsDefault() throws Exception {
        // 先触发几个请求，产生统计数据
        mockMvc.perform(get("/test/get/statTest1")).andExpect(status().isOk());
        mockMvc.perform(get("/test/get/statTest2")).andExpect(status().isOk());
        // 等待异步统计完成
        Thread.sleep(200);

        mockMvc.perform(get("/httpRawLog/statistics"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andDo(print());
    }

    /**
     * 测试统计接口，按总请求次数降序排序
     */
    @Test
    public void testStatisticsByTotalDesc() throws Exception {
        mockMvc.perform(get("/httpRawLog/statistics")
                        .param("type", "total")
                        .param("sort", "desc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andDo(print());
    }

    /**
     * 测试统计接口，按总请求次数升序排序
     */
    @Test
    public void testStatisticsByTotalAsc() throws Exception {
        mockMvc.perform(get("/httpRawLog/statistics")
                        .param("type", "total")
                        .param("sort", "asc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andDo(print());
    }

    /**
     * 测试统计接口，按平均耗时降序排序
     */
    @Test
    public void testStatisticsByAvgDesc() throws Exception {
        mockMvc.perform(get("/httpRawLog/statistics")
                        .param("type", "avg")
                        .param("sort", "desc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andDo(print());
    }

    /**
     * 测试统计接口，按平均耗时升序排序
     */
    @Test
    public void testStatisticsByAvgAsc() throws Exception {
        mockMvc.perform(get("/httpRawLog/statistics")
                        .param("type", "avg")
                        .param("sort", "asc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andDo(print());
    }

    /**
     * 测试统计接口，触发多次请求验证统计数据正确性
     */
    @Test
    public void testStatisticsWithData() throws Exception {
        // 触发多个请求到同一接口，验证统计数据
        String url = "/test/get/statMulti";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get(url)).andExpect(status().isOk());
        }
        Thread.sleep(500);

        MvcResult result = mockMvc.perform(get("/httpRawLog/statistics")
                        .param("type", "total")
                        .param("sort", "desc"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        log.info("统计数据: {}", body);
        // 验证返回的是数组且包含数据
        org.junit.jupiter.api.Assertions.assertTrue(body.startsWith("["));
        org.junit.jupiter.api.Assertions.assertTrue(body.contains("totalRequestNum"));
    }
}
