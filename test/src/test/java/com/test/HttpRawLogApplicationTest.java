package com.test;

import com.skycong.httprawlog2.HttpRawLogApplication;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Lists;
import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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

import javax.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = HttpRawLogApplication.class)
@ComponentScan("com.skycong.httprawlog2")
@AutoConfigureMockMvc
@Slf4j
public class HttpRawLogApplicationTest {

    @BeforeAll
    static void BeforeAll() {

    }

    @BeforeEach
    void BeforeEach() throws Exception {

    }

    @Autowired
    MockMvc mockMvc;


    @Test
    public void getTests() throws Exception {
        LinkedMultiValueMap<String, String> linkedMultiValueMap = new LinkedMultiValueMap<>();
        linkedMultiValueMap.put("abc", Lists.list("这是参数abc"));
        linkedMultiValueMap.put("p", Lists.list("这是参数abc"));
        Cookie token = mockMvc.perform(
                        get("/test/get/login")
                                .queryParams(linkedMultiValueMap))
                .andExpect(content().string(StringContains.containsString("公元")))
                .andDo(print())
                .andReturn().getResponse().getCookie("token");
    }

    @Test
    public void postFormTests() throws Exception {
        mockMvc.perform(post("/test/upload/werq")
                        .queryParam("query1", "123", "23443")
                        .queryParam("query2", "艾地苯醌@￥@", URLEncoder.encode("querystring参数需要用URL 编码传参", "utf8"))
                        .param("formdata1", "表单参数1")
                        .param("formdata2", "表单参数2")

                )
                .andExpect(content().string(StringContains.containsString("OK")))
                .andDo(print());
    }

    @Test
    public void postFileTests() throws Exception {
        MockMultipartFile firstFile = new MockMultipartFile("file2", "测试txt.log",
                MediaType.TEXT_PLAIN_VALUE, new ByteArrayInputStream("测试奔波\n将计就计".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(multipart("/test/upload/werq")
                .file(firstFile)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .queryParam("query1", "123", "23443")
                .queryParam("query2", "艾地苯醌@￥@", URLEncoder.encode("querystring参数需要用URL 编码传参", "utf8"))
                .param("formdata1", "表单参数1")
                .param("formdata2", URLEncoder.encode("表单参数2", "utf8")))
                .andExpect(content().string(StringContains.containsString("OK")))
                .andDo(print());
    }

    @Test
    public void postJsonTests() throws Exception {
        mockMvc.perform(post("/test/post")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\n" +
                                "  \"s1\": \"哎哎哎\",\n" +
                                "  \"int2\": 30,\n" +
                                "  \"adbc\": true\n" +
                                "}"))
                .andExpect(content().string(StringContains.containsString("OK")))
                .andDo(print());
    }

    @Test
    public void downloadTests() throws Exception {
        mockMvc.perform(get("/test/download/234"))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andDo(print());
    }

    @AfterEach
    void AfterEach() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/httpRawLog/history"))
                .andExpect(status().isOk())
                .andDo(print())
                .andReturn();
    }

    @AfterAll
    static void AfterAll() {

    }

}
