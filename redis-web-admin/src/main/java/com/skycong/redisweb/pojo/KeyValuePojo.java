package com.skycong.redisweb.pojo;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * <a href="http://www.redis.cn/commands.html">Redis 文档</a>
 *
 * @author ruanmingcong (005163)
 * @since 2022/5/24 15:02
 */
@Data
public class KeyValuePojo {

    /**
     * 单个key
     */
    private String key;
    /**
     * 批量key
     */
    private Set<String> keys;

    /**
     * 旧key
     */
    private String srcKey;
    /**
     * 新key
     */
    private String destKey;

    /**
     * 是否删除所有，默认为false
     */
    private boolean delAll = false;

    /**
     * <pre>
     *
     * 1，string，  {@link #newValue} 为新值
     * 2，list，
     *      updateAll = true ，{@link #listValue} 必须是list  [a,b,c]
     *      updateAll = false , {@link #newValue} 为新值，{@link #index}为该值的索引
     * 3，set
     *      updateAll = true, {@link #listValue}  必须有值，全量更新
     *      updateAll = false, 单个更新，{@link #oldValue} 旧值，{@link #newValue} 新值
     * 4，zset
     *      updateAll = true,  {@link #zsets} 必须有值
     *      updateAll = false, 单个更新，{@link #oldValue}为旧值，{@link #newZset}为新值
     * 5，hash
     *      updateAll = true,  {@link #mapValue} 必须有值，全量更新
     *      updateAll = false, {@link #newValue} 为新的hashKey值，  {@link #oldValue} 为旧的hashKey值， {@link #hashValue} 为新的值，
     *  6，stream
     *      updateAll = true, mapValue 必须有值，全量更新
     *      updateAll = false, hashKey 和  stringValue 必须有值，更新hash中指定的hashkey
     * </pre>
     * {@link org.springframework.data.redis.connection.DataType}
     */
    private String type = "string";

    /**
     * 新值
     */
    private String newValue;

    /**
     * 旧值
     */
    private String oldValue;

    /**
     * list，set，zset， 专用数据结构
     */
    private List<String> listValue;

    /**
     * hash 专用数据结构
     */
    private String hashValue;
    /**
     * hash 专用数据结构
     */
    private Map<String, String> mapValue;

    /**
     * 全量更新
     */
    private boolean updateAll = false;

    /**
     * 单个数据，指定index
     */
    private int index = -1;
    /**
     * zset 批量操作，
     */
    private Set<Zset> zsets;
    /**
     * zset 单个操作
     */
    private Zset newZset;
    /**
     * ttl ，-1 不过期
     */
    private int ttl = -1;

    /**
     * {@link org.springframework.data.redis.core.DefaultTypedTuple}
     */
    @Data
    public static class Zset {
        Double score;
        String value;
    }
}
