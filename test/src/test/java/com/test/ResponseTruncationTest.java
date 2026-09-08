package com.test;

import com.skycong.httprawlog2.HttpRawLogApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

/**
 * 验证 ResponseWrapper 是否截断了超过 1MB 的响应体，
 * 导致客户端收到的内容不完整。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = HttpRawLogApplication.class)
@ComponentScan("com.skycong.httprawlog2")
@AutoConfigureMockMvc
public class ResponseTruncationTest {

    @Autowired
    MockMvc mockMvc;

    /**
     * /test/largeResponse 返回 2MB 字符串。
     * 如果 ResponseWrapper 的 MAX_RESPONSE_BODY_SIZE (1MB) 截断了内容，
     * 客户端收到的长度会小于 2MB。
     */
    @Test
    void largeResponseBodyShouldNotBeTruncated() throws Exception {
        int expectedSize = 2 * 1024 * 1024;

        MvcResult result = mockMvc.perform(get("/test/largeResponse"))
                .andDo(print())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        System.out.println("Response body size: " + body.length() + " bytes");
        System.out.println("Expected size: " + expectedSize + " bytes");

        assertEquals(expectedSize, body.length(),
                "Response body was truncated! Expected " + expectedSize + " but got " + body.length());
    }
}
