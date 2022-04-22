package com.skycong.httprawlog.logrecord;

import com.skycong.logrecord.service.FunctionService;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author ruanmingcong (005163) on 2022/4/22 18:25
 */
@Component
public class FunctionServiceImpl implements FunctionService {
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

    public static String reverseString(String input) {
        StringBuilder backwards = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            backwards.append(input.charAt(input.length() - 1 - i));
        }
        return backwards.toString();
    }
}
