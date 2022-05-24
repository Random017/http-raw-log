package com.skycong.redisweb.controller;

import com.skycong.redisweb.pojo.KeyValuePojo;
import com.skycong.redisweb.service.RedisWebService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author ruanmingcong (005163)
 * @since 2022/5/24 11:21
 */
@Slf4j
@RestController
@RequestMapping("redisWebAdmin")
public class RedisWebController {

    private final RedisWebService redisWebService;

    public RedisWebController(RedisWebService redisWebService) {
        this.redisWebService = redisWebService;
        log.debug("init RedisWebController with RedisWebService[{}]", redisWebService.toString());
    }

    /*
     *
     * 1, 查看本机redis 数据库，key数量，info
     * 2， key crud
     * 新增key
     * 查找key，模糊查找，精确查找, 分页展示
     * 修改key
     * 删除key （删除全部）
     * 刷新
     *
     * 3，key的value展示
     * value crud
     *
     * 展示value，服务端以 string-utf 格式返回，展示有text，hex，json等
     * 修改保存，修改的内容的值都是以 string 类型呈现，保存时需要注意转换
     * 删除
     * 修改ttl
     * 刷新
     *
     *
     *
     *
     * */

    /**
     * redis info
     * 服务器，
     * 内存，
     * 状态，
     * 数据库信息
     */
    @GetMapping("info")
    Object info() {
        return redisWebService.info();
    }

    /**
     * 数据库信息
     */
    @GetMapping("dbInfo")
    Object dbInfo() {
        return redisWebService.dbInfo();
    }

    /**
     * 加载keys
     *
     * @param key   搜索的key 关键字
     * @param exact 是否精确匹配，默认false
     */
    @GetMapping("keys")
    Object keys(@RequestParam(value = "key", required = false, defaultValue = "") String key,
                @RequestParam(value = "exact", required = false, defaultValue = "false") boolean exact) {
        return redisWebService.keys(key, exact);
    }


    /**
     * 新增key
     */
    @PostMapping("addKey")
    Object addKey(@RequestBody KeyValuePojo keyValuePojo) {
        return redisWebService.addKey(keyValuePojo);
    }

    /**
     * set ttl
     */
    @PostMapping("setTtl")
    Object setTtl(@RequestBody KeyValuePojo keyValuePojo) {
        return redisWebService.setTtl(keyValuePojo);
    }


    /**
     * 获取key的值
     *
     * @param key 搜索的key 关键字
     *
     * @return eg
     * <pre>
     *  type = string，value = string
     *  type = list，value = list
     *  type = set value = set
     *  type = zset value = zset
     *  type = hash value = map
     * </pre>
     */
    @GetMapping("getKey")
    Object getKey(String key) {
        return redisWebService.getKey(key);
    }

    /**
     * 删除key
     */
    @PostMapping("delKeys")
    Object delKeys(@RequestBody KeyValuePojo keyValuePojo) {
        return redisWebService.delKeys(keyValuePojo);
    }

    /**
     * 删除 集合中的值
     */
    @PostMapping("delValue")
    Object delValue(@RequestBody KeyValuePojo keyValuePojo) {
        return redisWebService.delValue(keyValuePojo);
    }

    /**
     * 重命名 key
     */
    @PostMapping("renameKey")
    Object renameKey(@RequestBody KeyValuePojo keyValuePojo) {
        return redisWebService.renameKey(keyValuePojo);
    }


}
