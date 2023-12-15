package com.skycong.httprawlog;

import java.util.HashSet;
import java.util.Set;

/**
 * @author ruanmingcong
 * @since 2022/8/11 15:58
 */
public interface Constant {

    String UTF8 = "utf-8";
    String DEFAULT_CHARACTER_ENCODING = "ISO-8859-1";

    /**
     * 表单请求，请求体如下
     * <pre>
     * ----------------------------661737071028691801410130
     * Content-Disposition: form-data; name="form1"
     *
     * form-data-
     * ----------------------------661737071028691801410130
     * Content-Disposition: form-data; name="form2"
     *
     * 含自费啊1123
     * ----------------------------661737071028691801410130
     * Content-Disposition: form-data; name="file"; filename="v2-2682bb9c3675ae1dbb0cb752bda61532_720w.jpg"
     * Content-Type: image/jpeg
     * </pre>
     * 所以请求中存在普通表单key=value，也有可能存在文件流，需要对其近特殊处理，否则log 将直接打印出原始文件字节流
     */
    String FORM_DATA = "multipart/form-data";

    /**
     * 普通表单数据，请求体如下
     * <pre>
     *     form1=form-data-&form2=%E5%90%AB%E8%87%AA%E8%B4%B9%E5%95%8A1123
     * </pre>
     * 表单数据被URL编码了，需要解码操作
     */
    String FORM_URLENCODED = "application/x-www-form-urlencoded";

    String FILTER_NAME = "HttpRawLogFilter";
    String EMPTY = "";
    String STRING0 = "&";
    String SPLIT = ",";
    String EQ = "=";
    String STRING3 = ";";
    String LINE = "\n";
    String CONTENT_DISPOSITION = "Content-Disposition";
    String APPLICATION_OCTET_STREAM = "APPLICATION/OCTET-STREAM";
    String CONTENT_TYPE = "content-type";
    String LOG_URLS = "com.skycong.http-raw.log.urls";
    String LOG_URL_EXCLUDE = "com.skycong.http-raw.log.url.exclude";
    String LOG_URL_EXCLUDE_SUFFIX = "com.skycong.http-raw.log.url.exclude-suffix";
    @Deprecated
    String LOG_QUERY_STRING_ENCODE = "com.skycong.http-raw.log.query-string.encode";
    String LOG_FORM_DATA_ENCODE = "com.skycong.http-raw.log.form-data.encode";
    String LOG_HEADERS = "com.skycong.http-raw.log.headers";
    String LOG_STATISTICS = "com.skycong.http-raw.log.statistics";
    String STRING4 = "/*";
    String JS_CSS_HTML = "js,css,html";
    String LOG_ID = "hLogId";

    /**
     * 默认 排除的Url
     */
    Set<String> DEFAULT_URL_EXCLUDE_PATTERNS = new HashSet<String>() {
        {
            //前端文件
            add("/**/*.html");
            add("/**/*.js");
            add("/**/*.css");
            add("/**/*.jsp");
            // 图片
            add("/**/*.jpg");
            add("/**/*.png");
            add("/**/*.gif");
            add("/**/*.jpeg");
            add("/**/*.ico");
            add("/**/*.bmp");
            // 字体
            add("/**/*.ttf");
            add("/**/*.woff");
            // 音视频
            add("/**/*.mp3");
            add("/**/*.mp4");
            // 文档
            add("/**/*.word");
            add("/**/*.ppt");
            add("/**/*.xls");
            add("/**/*.xlsx");
            add("/**/*.pdf");
            // 自有接口
            add("/httpRawLog/*");
        }
    };

}
