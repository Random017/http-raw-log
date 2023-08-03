package com.test;

import com.github.xiaoymin.knife4j.core.util.StrUtil;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.AntPathMatcher;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;


/**
 * @author ruanmingcong (005163)
 * @since 2022/7/21 13:42
 */
@Slf4j
public class Test {
    /*
    *
    *  0 -db
    *  1 -redis
    *  2 -local memory
    *
    *
    * */

    @org.junit.Test
    public void test1() throws UnsupportedEncodingException {
        String s = "form1=form-data-&form2=%E5%90%AB%E8%87%AA%E8%B4%B9%E5%95%8A1123";
        System.out.println(URLDecoder.decode(s,"utf-8"));
        System.out.println(new String(s.getBytes(StandardCharsets.ISO_8859_1),StandardCharsets.UTF_8));
    }


    @org.junit.Test
    public void test2()   {
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        System.out.println(antPathMatcher.match("/test/*", "/test/abc"));
        System.out.println(antPathMatcher.match("/test/**", "/test/abc/asdf"));
        System.out.println(antPathMatcher.match("/test/*.jsp", "/test/abc.jsp"));
        System.out.println(antPathMatcher.match("/test/**/*.jsp", "/test/abc/123/abc.jsp"));
        System.out.println(antPathMatcher.match("/test/", "/test/abc"));
    }

    @org.junit.Test
    public void test3()   {
        log.error("\u001B[34m" + "打印日志" + "\u001B[0m");
    }

    @org.junit.Test
    public void test4()   {

        CharsetEncoder encoder = CharsetUtil.encoder(StandardCharsets.ISO_8859_1);
        System.out.println(encoder.canEncode('公'));
        String s = "公元 2023-08-03(第31周) 下午 04:05:07.020 GMT +0800";
        System.out.println(s);
        byte[] bytes1 = s.getBytes(StandardCharsets.UTF_8);
        System.out.println(Arrays.toString(bytes1));
        System.out.println(new String(bytes1, StandardCharsets.UTF_8));

        char[] chars = s.toCharArray();
        System.out.println(chars);


        byte[] bytes2 = s.getBytes(StandardCharsets.ISO_8859_1);

        System.out.println(Arrays.toString(bytes2));
        System.out.println(new String(bytes2, StandardCharsets.ISO_8859_1));
        System.out.println(new String(bytes2, StandardCharsets.UTF_8));

        log.error("\u001B[34m" + "打印日志" + "\u001B[0m");
    }


}
