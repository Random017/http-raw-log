package com.skycong.httprawlog.autoconfig;

import com.skycong.httprawlog.Constant;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 文件日志监听条件：必须同时配置 {@code appLogFilePath} 和 {@code history > 0}。
 *
 * <p>没有历史记录时 txId 关联没有意义，不应启动后台线程。
 *
 * @author ruanmingcong
 * @since 1.1.0
 */
public class FileAppenderEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        String filePath = environment.getProperty(Constant.APP_LOG_FILE_PATH);
        if (filePath == null || filePath.trim().isEmpty()) {
            return false;
        }
        String history = environment.getProperty(Constant.HISTORY_NUM);
        if (history == null || history.trim().isEmpty()) {
            return false;
        }
        try {
            return Integer.parseInt(history.trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
