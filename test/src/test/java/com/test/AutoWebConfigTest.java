package com.test;

import com.skycong.httprawlog.api.HistoryApi;
import com.skycong.httprawlog.api.HistoryStore;
import com.skycong.httprawlog.api.StatisticsApi;
import com.skycong.httprawlog.appender.FileAppender;
import com.skycong.httprawlog.autoconfig.AutoWebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动装配条件测试：不启动完整应用，验证开关、默认值、容错和 Web 类型条件。
 */
public class AutoWebConfigTest {

    private final WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
            .withUserConfiguration(AutoWebConfig.class);

    /**
     * 非 Servlet Web 应用（如 WebFlux）不注册任何组件。
     */
    @Test
    void nonWebContext_registersNothing() {
        new ApplicationContextRunner()
                .withUserConfiguration(AutoWebConfig.class)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(HistoryApi.class);
                    assertThat(context).doesNotHaveBean(StatisticsApi.class);
                    assertThat(context).doesNotHaveBean(FileAppender.class);
                });
    }

    /**
     * 默认：filter + history controller + statistics 全部装配，history 默认 0（不存储）。
     */
    @Test
    void defaults_registerFilterHistoryAndStatistics() {
        webRunner.run(context -> {
            assertThat(context).hasBean("httpRawLogFilterRegistration");
            assertThat(context).hasSingleBean(HistoryApi.class);
            assertThat(context).hasSingleBean(StatisticsApi.class);
            assertThat(context).hasSingleBean(HistoryStore.class);
            assertThat(context).doesNotHaveBean(FileAppender.class);
            assertThat(context.getBean(HistoryStore.class).getMaxHistory()).isZero();
        });
    }

    /**
     * 新开关 com.skycong.http-raw.log.open=false 关闭整个自动装配。
     */
    @Test
    void openFalse_disablesAllBeans() {
        webRunner.withPropertyValues("com.skycong.http-raw.log.open=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("httpRawLogFilterRegistration");
                    assertThat(context).doesNotHaveBean(HistoryApi.class);
                });
    }

    /**
     * 旧开关 com.skycong.http-raw.log=false 仍然生效，且整个自动装配（含调试端点）都不创建。
     */
    @Test
    void legacyOpenFalse_disablesAllBeans() {
        webRunner.withPropertyValues("com.skycong.http-raw.log=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean("httpRawLogFilterRegistration");
                    assertThat(context).doesNotHaveBean(HistoryApi.class);
                    assertThat(context).doesNotHaveBean(StatisticsApi.class);
                });
    }

    /**
     * statistics=false 时不再装配统计 Controller，避免无意义的端点暴露。
     */
    @Test
    void statisticsFalse_disablesStatisticsBean() {
        webRunner.withPropertyValues("com.skycong.http-raw.log.statistics=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(StatisticsApi.class);
                    assertThat(context).hasBean("httpRawLogFilterRegistration");
                });
    }

    /**
     * 配置写错（history=abc）不能导致宿主应用启动失败，回退为 0。
     */
    @Test
    void invalidHistoryValue_fallsBackToZero() {
        webRunner.withPropertyValues("com.skycong.http-raw.log.history=abc")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(HistoryStore.class).getMaxHistory()).isZero();
                });
    }

    /**
     * 配置了 appLogFilePath 且 history > 0 才启动文件监听，避免无意义的后台线程。
     */
    @Test
    void fileAppender_requiresPathAndPositiveHistory() {
        webRunner.withPropertyValues(
                        "com.skycong.http-raw.log.history=10",
                        "com.skycong.http-raw.log.appLogFilePath=build/test-app.log")
                .run(context -> assertThat(context).hasSingleBean(FileAppender.class));
    }
}
