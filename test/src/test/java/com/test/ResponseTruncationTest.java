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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 验证大响应：客户端收到完整内容；历史接口里的 responseBody 按存储上限截断。
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
     * 客户端必须收到完整 2MB；history 中的 responseBody 只保留 32KB 并带截断标记。
     */
    @Test
    void largeResponseBody_clientComplete_historyTruncated() throws Exception {
        int expectedSize = 2 * 1024 * 1024;

        MvcResult result = mockMvc.perform(get("/test/largeResponse"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertEquals(expectedSize, body.length(),
                "Response body was truncated! Expected " + expectedSize + " but got " + body.length());

        String hLogId = result.getResponse().getHeader("hLogId");
        assertNotNull(hLogId);
        MvcResult historyResult = mockMvc.perform(get("/httpRawLog/history").param("hLogId", hLogId))
                .andExpect(status().isOk())
                .andReturn();
        String historyJson = historyResult.getResponse().getContentAsString();
        assertTrue(historyJson.contains("truncated, total size ="),
                "history responseBody should be truncated; actual head: "
                        + historyJson.substring(0, Math.min(historyJson.length(), 400)));
        assertTrue(historyJson.contains("maxBodyBytes=32768"));
        assertTrue(historyJson.length() < 40 * 1024,
                "history JSON should stay bounded, actual=" + historyJson.length());
    }
}
