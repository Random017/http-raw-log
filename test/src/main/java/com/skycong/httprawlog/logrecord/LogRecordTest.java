package com.skycong.httprawlog.logrecord;

import com.skycong.logrecord.constant.InternalOperateType;
import com.skycong.logrecord.core.LogRecord;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * @author ruanmingcong (005163) on 2022/4/22 10:58
 */
@RestController
@RequestMapping("logRecord")
public class LogRecordTest {

    private static final Logger log = LoggerFactory.getLogger(LogRecordTest.class);


    @LogRecord(value = "'现在时间是' + #user.dateTime + ',姓名:'+ #user.name + ', 年龄:'+ #user.age + '返回结果'+ #_ret + #_errMsg",
            businessType = "test",
            operateType = InternalOperateType.Add)
    @RequestMapping("test1")
    public Object test1(User user) {
        log.info("" + user);

        // throw new IllegalArgumentException("抛异常了");

        SpelExpressionParser spelExpression = new SpelExpressionParser();
        Expression expression = spelExpression.parseExpression("#root.name");
        String value = expression.getValue(new User(), String.class);
        System.out.println(value);
        return "" + user.name;
    }

    @Autowired
    ApplicationContext applicationContext;

    @LogRecord(value = "'test2现在时间是' + #user.dateTime + #reverseString('test2现在时间是')", businessType = "test")
    // @LogRecord(value = "'test2现在时间是' + #user.dateTime + ',姓名:'+ #user.name + ', 年龄:'+ #user.age + '返回结果'+ #_ret + #_errMsg", businessType = "test")
    @RequestMapping("test2")
    public Object test2(User user) {
        new Thread(() -> {
            applicationContext.getBean(LogRecordTest.class).test1(user);
        }).start();
        return "safdsafsafsafsafsfsa";
    }

    public String abcd(String a) {
        return a + "123";
    }

    public static void main(String[] args) {
        SpelExpressionParser spelExpression = new SpelExpressionParser();
        // Expression expression = spelExpression.parseExpression("  '暗室逢灯撒旦法师法' + {#root.name} + #root.dateTime");
        Expression expression = spelExpression.parseExpression(" 'sadfsad大发啥地方撒放'");
        String value = expression.getValue(new User(), String.class);
        System.out.println(value);
    }


    @Data
    public static class User {
        String name = "asdfsfsa";
        String pass = System.currentTimeMillis() + "";
        int age = (int) (Math.random() * 1000);
        LocalDateTime dateTime = LocalDateTime.now();

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("User{");
            sb.append("name='").append(name).append('\'');
            sb.append(", pass='").append(pass).append('\'');
            sb.append(", age=").append(age);
            sb.append(", dateTime=").append(dateTime);
            sb.append('}');
            return sb.toString();
        }
    }

}
