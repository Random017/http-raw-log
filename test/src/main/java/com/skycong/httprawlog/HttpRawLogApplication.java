package com.skycong.httprawlog;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.filter.ApplicationContextHeaderFilter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.filter.ShallowEtagHeaderFilter;
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
    @GetMapping(value = "get/{p}")
    String get(@RequestParam(value = "abc", required = false) String abc,
               @PathVariable(value = "p", required = false) String p) {
        LOGGER.debug("abc = {},p = {}", abc, p);
        String s = getTime() + "\n" + abc + "\n" + p;
        LOGGER.info("s = {}", s);
        return s;
    }

    @GetMapping("getCall/{p}")
    Callable<Object> getCall(@RequestParam(value = "abc", required = false) String abc,
                             @PathVariable(value = "p", required = false) String p) {
        return () -> {
            System.out.println(Thread.currentThread().getName());
            LOGGER.debug("abc = {},p = {}", abc, p);
            return getTime() + "\n" + abc + "\n" + p;
        };
    }


    String getTime() {
        return new SimpleDateFormat("G yyyy-MM-dd(第w周) a hh:mm:ss.SSS 'GMT' Z", Locale.SIMPLIFIED_CHINESE).format(new Date());
    }

    @PostMapping("post")
    Object post(@RequestBody Pojo pojo) {
        LOGGER.debug("pojo" + pojo);
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
        LOGGER.debug("file = {},file2 = {},query1 = {},query12 = {},formdata1 = {},formdata2 = {}, abc = {}"
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

    @Bean
    public CommonsRequestLoggingFilter commonsRequestLoggingFilter() {
        CommonsRequestLoggingFilter filter = new CommonsRequestLoggingFilter();
        filter.setIncludeQueryString(true);
        filter.setIncludeClientInfo(true);
        filter.setIncludeHeaders(true);
        filter.setIncludePayload(true);
        return filter;
    }

    @Bean
    public ApplicationContextHeaderFilter applicationContextHeaderFilter(ApplicationContext applicationContext) {
        return new ApplicationContextHeaderFilter(applicationContext);
    }

    @Bean
    public ShallowEtagHeaderFilter shallowEtagHeaderFilter() {
        return new ShallowEtagHeaderFilter();
    }


}
