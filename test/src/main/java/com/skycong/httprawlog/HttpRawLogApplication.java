package com.skycong.httprawlog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.Map;

@SpringBootApplication
@RestController
@RequestMapping("test")
@ComponentScan(basePackages = "com.skycong")
public class HttpRawLogApplication {


    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRawLogApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(HttpRawLogApplication.class, args);
    }


    /**
     * 测试普通get
     */
    @GetMapping("get/{p}")
    Object get(@RequestParam(value = "abc", required = false) String abc,
               @PathVariable(value = "p", required = false) String p) {
        LOGGER.debug("abc = {},p = {}", abc, p);
        return "OK";
    }

    @PostMapping("post")
    Object post(Map<String, Object> map) {
        LOGGER.debug("" + map);
        return "OK";
    }

    @RequestMapping("all")
    Object all(HttpServletResponse response) {
        response.addHeader("abc", String.valueOf(System.currentTimeMillis()));
        return "OK";
    }


    @PostMapping("upload/{p}")
    Object upload(@RequestParam(value = "file", required = false) MultipartFile file,
                  @RequestParam(value = "abc", required = false) String abc,
                  @PathVariable(value = "p", required = false) String p) {
        LOGGER.debug("file = {},abc = {},p = {}", file, abc, p);
        return "OK";
    }


    @GetMapping("download/{p}")
    void downlaod(@RequestParam(value = "file", required = false) MultipartFile file,
                  @RequestParam(value = "abc", required = false) String abc,
                  @PathVariable(value = "p", required = false) String p,
                  HttpServletResponse response) throws IOException {
        LOGGER.debug("file = {},abc = {},p = {}", file, abc, p);

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
