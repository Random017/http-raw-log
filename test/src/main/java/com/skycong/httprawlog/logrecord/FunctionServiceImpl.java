package com.skycong.httprawlog.logrecord;

import com.skycong.logrecord.pojo.LogRecordPojo;
import com.skycong.logrecord.service.LogRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author ruanmingcong (005163) on 2022/4/22 18:25
 */
@Component
public class FunctionServiceImpl implements LogRecordService {
    @Override
    public Map<String, Method> getFunctions() {
        Method reverseString = null;
        try {
            reverseString = FunctionServiceImpl.class.getDeclaredMethod("reverseString", String.class);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
        HashMap<String, Method> map = new HashMap<>();
        map.put("reverseString", reverseString);
        return map;
    }


    @Autowired(required = false)
    @Qualifier(value = "taskExecutor")
    ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 5, TimeUnit.MINUTES, new LinkedBlockingDeque<>());

    @Override
    public String getCurrentOperator(String operator) {
        System.out.println(operator);

        return "" + System.currentTimeMillis();
    }

    @Override
    public void record(LogRecordPojo logRecordPojo) {
        executor.submit(() -> {
            System.out.println("实现 RecordLogServiceImpl");
            System.out.println(logRecordPojo);
        });
    }

    public static String reverseString(String input) {
        StringBuilder backwards = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            backwards.append(input.charAt(input.length() - 1 - i));
        }
        return backwards.toString();
    }
}
