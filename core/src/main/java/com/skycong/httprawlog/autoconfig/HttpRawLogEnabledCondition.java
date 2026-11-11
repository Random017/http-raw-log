package com.skycong.httprawlog.autoconfig;

import com.skycong.httprawlog.Constant;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 开关条件：{@code com.skycong.http-raw.log.open=false} 关闭。
 *
 * <p>使用 {@link Boolean#parseBoolean(String)} 而不是 SpEL/属性类型转换，配置值写错时按“关闭”处理，
 * 不会让宿主应用启动失败；同时兼容旧开关 {@code com.skycong.http-raw.log=false}。
 *
 * @author ruanmingcong
 * @since 1.1.0
 */
public class HttpRawLogEnabledCondition implements Condition {

    /**
     * 旧版本开关（已废弃），仅在 open 未配置时生效
     */
    private static final String LEGACY_OPEN = "com.skycong.http-raw.log";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        String value = environment.getProperty(Constant.LOG_OPEN);
        if (value == null) {
            value = environment.getProperty(LEGACY_OPEN);
        }
        return value == null || Boolean.parseBoolean(value.trim());
    }
}
