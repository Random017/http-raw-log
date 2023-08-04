package com.skycong.httprawlog2;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.Callable;

/**
 * @author ruanmingcong (005163)
 * @since 23/08/03 16:53
 */
@Slf4j
@RestController
@RequestMapping("test")
public class TestController {


    /**
     * 测试普通get
     */
    @GetMapping(value = "get/{p}")
    String get(@RequestParam(value = "abc", required = false) String abc,
               @PathVariable(value = "p", required = false) String p, HttpServletResponse response) {
        log.debug("abc = {},p = {}", abc, p);
        String s = getTime() + "\n" + abc + "\n" + p;
        log.info("s = {}", s);
        response.setCharacterEncoding("utf8");
        return s;
    }

    @GetMapping("getCall/{p}")
    Callable<Object> getCall(@RequestParam(value = "abc", required = false) String abc,
                             @PathVariable(value = "p", required = false) String p) {
        return () -> {
            System.out.println(Thread.currentThread().getName());
            log.debug("abc = {},p = {}", abc, p);
            return getTime() + "\n" + abc + "\n" + p;
        };
    }


    String getTime() {
        return new SimpleDateFormat("G yyyy-MM-dd(第w周) a hh:mm:ss.SSS 'GMT' Z", Locale.SIMPLIFIED_CHINESE).format(new Date());
    }

    @PostMapping("post")
    Object post(@RequestBody Pojo pojo) {
        log.debug("pojo" + pojo);
        return "OK:" + pojo.toString();
    }

    @Data
    static class Pojo {
        String s1;
        Integer int2;
        Boolean adbc;

    }

    @RequestMapping("all")
    Object all(HttpServletResponse response) {
        response.addHeader("abc", String.valueOf(System.currentTimeMillis()));
        return "OK";
    }


    @PostMapping("upload/{p}")
    Object upload(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "file2", required = false) MultipartFile file2,
            @RequestParam(value = "query1", required = false) String query1,
            @RequestParam(value = "query2", required = false) String query2,
            @RequestParam(value = "formdata1", required = false) String formdata1,
            @RequestParam(value = "formdata2", required = false) String formdata2,
            @RequestParam(value = "abc", required = false) String abc,
            @PathVariable(value = "p", required = false) String p) throws IOException {
        log.debug("file = {},file2 = {},query1 = {},query12 = {},formdata1 = {},formdata2 = {}, abc = {}"
                , file, file2, query1, query2, formdata1, formdata2, abc);

        if (file != null) {
            Path tmp = Files.createTempFile("123", "tmp");
            System.out.println(tmp);
            file.transferTo(tmp);
        }
        return "OK:\n" + file + "\n" + file2 + "\n" + query1 + "\n" + query2 + "\n" + formdata1 + "\n" + formdata2 + "\n" + abc + "\n" + p;
    }


    @GetMapping("download/{p}")
    void downlaod(@RequestParam(value = "file", required = false) MultipartFile file,
                  @RequestParam(value = "abc", required = false) String abc,
                  @PathVariable(value = "p", required = false) String p,
                  HttpServletResponse response) throws IOException {
        log.debug("file = {},abc = {},p = {}", file, abc, p);

        String downloadContent = System.currentTimeMillis() + " 这是下载内容abc123!@#$";

        String downloadFilename = URLEncoder.encode("abc123中文", "utf8");
        // 指明response的返回对象是文件流
        response.setContentType("application/octet-stream");
        // 设置在下载框默认显示的文件名
        response.setHeader("Content-Disposition", "attachment;filename=" + downloadFilename);
        response.getOutputStream().write(downloadContent.getBytes());
        response.getOutputStream().flush();
    }
}
