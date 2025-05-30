package com.skycong.httprawlog.appender;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author ruanmingcong
 * @since 2025/5/29 16:21
 */
public class InMemoryAppender extends AppenderBase<ILoggingEvent> {



    // // 获取Logback的LoggerContext
    // LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    //
    // InMemoryAppender inMemoryAppender = new InMemoryAppender();
    // // 配置并启动自定义Appender
    // inMemoryAppender.setContext(context);
    // inMemoryAppender.setName("IN_MEMORY");
    // inMemoryAppender.start();
    //
    // // 将Appender添加到root logger
    // ch.qos.logback.classic.Logger rootLogger = context.getLogger("ROOT");
    // rootLogger.addAppender(inMemoryAppender);


    private static final int MAX_SIZE = 1000; // 限制日志数量，避免内存溢出
    public static final List<ILoggingEvent> events = Collections.synchronizedList(new ArrayList<>());

    @Override
    protected void append(ILoggingEvent eventObject) {
        synchronized (events) {
            events.add(eventObject);
            // 保持固定大小，超出部分移除最早的日志
            if (events.size() > MAX_SIZE) {
                events.remove(0);
            }
        }
    }

    // 获取所有日志
    public List<ILoggingEvent> getAllEvents() {
        synchronized (events) {
            return new ArrayList<>(events);
        }
    }

    // 清空日志
    public void clearEvents() {
        synchronized (events) {
            events.clear();
        }
    }
}
