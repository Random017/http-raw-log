package com.skycong.redisweb.controller;

import com.github.xiaoymin.knife4j.annotations.ApiOperationSupport;
import com.github.xiaoymin.knife4j.annotations.ApiSupport;
import com.github.xiaoymin.knife4j.annotations.DynamicParameter;
import com.github.xiaoymin.knife4j.annotations.DynamicParameters;
import com.skycong.redisweb.pojo.KeyValuePojo;
import com.skycong.redisweb.service.RedisWebService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
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
@ApiSupport(order = -1)
@Api(tags = "redis-web-admin 接口")
@RestController
@RequestMapping("redisWebAdmin")
@Slf4j
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
    // @ApiOperation(value = "info 接口")
    @ApiOperationSupport(order = 1)
    @GetMapping("info")
    Object info() {
        return redisWebService.info();
    }

    /**
     * 数据库信息
     */
    @ApiOperationSupport(order = 2)
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
    @ApiOperationSupport(order = 3)
    @GetMapping("keys")
    Object keys(@RequestParam(value = "key", required = false, defaultValue = "") String key,
                @RequestParam(value = "exact", required = false, defaultValue = "false") boolean exact) {
        return redisWebService.keys(key, exact);
    }


    /**
     * 新增key
     */
    @ApiOperationSupport(order = 5)
    @PostMapping("addOrUpdate")
    Object addOrUpdate(@RequestBody KeyValuePojo keyValuePojo) {
        return redisWebService.addOrUpdate(keyValuePojo);
    }

    /**
     * set ttl
     */
    @ApiOperationSupport(order = 7)
    @PostMapping("setTtl")
    Object setTtl(@RequestBody KeyValuePojo keyValuePojo) {
        return redisWebService.setTtl(keyValuePojo);
    }


    /**
     * 获取key的值
     *
     * @param key 搜索的key 关键字
     * @return eg
     * <pre>
     *  type = string，value = string
     *  type = list，value = list
     *  type = set value = set
     *  type = zset value = zset
     *  type = hash value = map
     * </pre>
     */
    @ApiOperationSupport(order = 4)
    @GetMapping("getKey")
    Object getKey(String key) {
        return redisWebService.getKey(key);
    }

    /**
     * 删除key
     */
    @ApiOperation(value = "删除key", notes = "")
    @ApiOperationSupport(order = 9, author = "mc",includeParameters = {"keyValuePojo.key"})
    @PostMapping("delKeys")
    Object delKeys(@RequestBody KeyValuePojo keyValuePojo) {
        return redisWebService.delKeys(keyValuePojo);
    }

    /**
     * 删除 集合中的值
     */
    @ApiOperationSupport(order = 10)
    @PostMapping("delValue")
    Object delValue(@RequestBody KeyValuePojo keyValuePojo) {
        return redisWebService.delValue(keyValuePojo);
    }

    /**
     * 重命名 key
     */
    @ApiOperationSupport(order = 6)
    @PostMapping("renameKey")
    Object renameKey(@RequestBody KeyValuePojo keyValuePojo) {
        return redisWebService.renameKey(keyValuePojo);
    }

    /**
     * persist key
     */
    @ApiOperationSupport(order = 8)
    @PostMapping("persist")
    Object persist(@RequestBody KeyValuePojo keyValuePojo) {
        return redisWebService.persist(keyValuePojo);
    }

}
