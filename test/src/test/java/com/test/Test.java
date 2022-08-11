package com.test;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * @author ruanmingcong (005163)
 * @since 2022/7/21 13:42
 */
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


}