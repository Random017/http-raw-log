package com.skycong.httprawlog.logrecord;

import com.skycong.logrecord.service.OperatorService;
import org.springframework.stereotype.Component;

/**
 * @author ruanmingcong (005163) on 2022/4/22 16:46
 */
@Component
public class OperatorServiceImpl implements OperatorService {

    @Override
    public String getCurrentOperator(String operator) {
        System.out.println(operator);

        return "" + System.currentTimeMillis();
    }


}
