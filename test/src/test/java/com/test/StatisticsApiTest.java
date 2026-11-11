package com.test;

import com.skycong.httprawlog.api.StatisticsApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 统计 URI 归一化测试：防止路径参数/随机 ID 把统计 map 撑爆。
 */
public class StatisticsApiTest {

    @Test
    void numericPathVariables_areNormalized() {
        assertEquals("/users/{id}/orders/{id}",
                StatisticsApi.normalizeUri("/users/123/orders/456"));
    }

    @Test
    void uuidPathVariables_areNormalized() {
        assertEquals("/orders/{id}",
                StatisticsApi.normalizeUri("/orders/550e8400-e29b-41d4-a716-446655440000"));
    }

    @Test
    void queryAndFragment_areRemoved() {
        assertEquals("/test/get", StatisticsApi.normalizeUri("/test/get?abc=1#section"));
    }

    @Test
    void staticPath_isUnchanged() {
        assertEquals("/test/get/abc", StatisticsApi.normalizeUri("/test/get/abc"));
    }
}
