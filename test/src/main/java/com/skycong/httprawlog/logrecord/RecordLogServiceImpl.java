package com.skycong.httprawlog.logrecord;

import com.skycong.logrecord.pojo.LogRecordPojo;
import com.skycong.logrecord.service.RecordLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author ruanmingcong (005163) on 2022/4/22 16:48
 */
@Component
public class RecordLogServiceImpl implements RecordLogService {

    @Autowired(required = false)
    @Qualifier(value = "taskExecutor")
    ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 5, TimeUnit.MINUTES, new LinkedBlockingDeque<>());

    @Override
    public void record(LogRecordPojo logRecordPojo) {
        executor.submit(() -> {
            System.out.println("实现 RecordLogServiceImpl");
            System.out.println(logRecordPojo);
        });
    }


}
