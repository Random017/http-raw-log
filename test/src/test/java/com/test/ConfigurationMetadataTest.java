package com.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skycong.httprawlog.Constant;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 手写 spring-configuration-metadata.json 与 {@link Constant} 的一致性守卫。
 *
 * <p>元数据目前仍由人工维护；新增配置项却忘记补元数据时，这个测试会失败，
 * 避免 IDE 提示与真实配置长期漂移。
 */
public class ConfigurationMetadataTest {

    @Test
    void everyPropertyConstantHasMetadata() throws Exception {
        JsonNode root = new ObjectMapper().readTree(
                new ClassPathResource("META-INF/spring-configuration-metadata.json").getInputStream());
        Set<String> metadataNames = new HashSet<>();
        root.path("properties").forEach(property -> metadataNames.add(property.path("name").asText()));

        List<String> missing = new ArrayList<>();
        for (Field field : Constant.class.getFields()) {
            if (field.getType() != String.class) {
                continue;
            }
            String value = (String) field.get(null);
            if (!value.startsWith("com.skycong.http-raw.log")) {
                continue;
            }
            // 已废弃且不再读取的旧开关，不要求出现在元数据里
            if ("com.skycong.http-raw.log.query-string.encode".equals(value)) {
                continue;
            }
            if (!metadataNames.contains(value)) {
                missing.add(field.getName() + "=" + value);
            }
        }
        assertTrue(missing.isEmpty(), "spring-configuration-metadata.json 缺少配置项: " + missing);
    }

    /**
     * 描述也要跟随行为更新：unknown-length 已经支持普通 chunked，不能再写“长度未知时不再包装”。
     */
    @Test
    void descriptionsMatchCurrentRequestBehavior() throws Exception {
        JsonNode root = new ObjectMapper().readTree(
                new ClassPathResource("META-INF/spring-configuration-metadata.json").getInputStream());

        List<String> emptyDescriptions = new ArrayList<>();
        List<String> staleDescriptions = new ArrayList<>();
        for (JsonNode property : root.path("properties")) {
            String name = property.path("name").asText();
            String description = property.path("description").asText();
            if (description.isEmpty()) {
                emptyDescriptions.add(name);
            }
            if (description.contains("长度未知时不再包装")) {
                staleDescriptions.add(name);
            }
        }
        assertTrue(emptyDescriptions.isEmpty(), "配置项缺少 description: " + emptyDescriptions);
        assertTrue(staleDescriptions.isEmpty(), "配置项仍声称未知长度不包装: " + staleDescriptions);

        JsonNode maxRequestBody = null;
        for (JsonNode property : root.path("properties")) {
            if ("com.skycong.http-raw.log.maxRequestBodyBytes".equals(property.path("name").asText())) {
                maxRequestBody = property;
                break;
            }
        }
        assertTrue(maxRequestBody != null, "maxRequestBodyBytes metadata should exist");
        String description = maxRequestBody.path("description").asText();
        assertTrue(description.contains("chunked"), "maxRequestBodyBytes description should mention chunked: " + description);
        assertFalse(description.contains("长度未知时不再包装"), description);
    }
}
