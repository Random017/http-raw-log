package com.skycong.httprawlog2;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试用回声接口。
 * 原样接收请求体并记录，供集成测试断言“业务侧拿到的 body 完整”（与日志截断互不影响）。
 */
@RestController
@RequestMapping("test")
public class EchoTestController {

    /** 最近一次收到的请求体，供集成测试读取，验证业务 body 完整性 */
    public static volatile String lastReceivedBody;

    @PostMapping("echo")
    public String echo(@RequestBody String body) {
        lastReceivedBody = body;
        return "OK";
    }
}
