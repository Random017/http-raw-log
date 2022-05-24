package com.skycong.redisweb.service;

import com.skycong.redisweb.pojo.KeyValuePojo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.types.RedisClientInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author ruanmingcong (005163)
 * @since 2022/5/24 11:21
 */
@Slf4j
public class RedisWebService {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisWebService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
        log.debug("init RedisWebService[{}] with RedisTemplate[{}]", this, redisTemplate.toString());
    }


    public Map<Object, Object> info() {
        List<RedisClientInfo> clientList = redisTemplate.getClientList();
        Properties info = redisTemplate.getRequiredConnectionFactory().getConnection().info();
        assert info != null;
        Map<Object, Object> hashMap = new HashMap<>(info);
        hashMap.put("clientList", clientList);
        return hashMap;
    }

    public Object dbInfo() {
        Map<Object, Object> info = info();
        return info.get("db0");
    }

    /**
     * 加载keys
     *
     * @param key   搜索的key 关键字
     * @param exact 是否精确匹配，默认false
     */
    public List<String> keys(String key, boolean exact) {
        // 模糊扫描
        if (!exact) {
            key = "*" + key + "*";
        }
        Cursor<byte[]> scan = redisTemplate.getRequiredConnectionFactory().getConnection()
                .scan(ScanOptions.scanOptions()
                        .match(key)
                        .count(10000)
                        .build());

        List<String> keys = new ArrayList<>(256);
        try {
            while (scan.hasNext()) {
                keys.add(new String(scan.next(), StandardCharsets.UTF_8));
            }
        } finally {
            try {
                scan.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return keys;
    }

    /**
     * 获取对应key的值
     */
    public Object getKey(String key) {
        HashMap<String, Object> res = new HashMap<>(2);
        // 值，依据类型对应不同的类型，list ，set hash
        Object value;
        // string[字符串的字节大小]，list、set、zset、hash 元素个数
        Long size;
        DataType type = redisTemplate.type(key);
        if (type == DataType.STRING) {
            value = redisTemplate.opsForValue().get(key);
            size = redisTemplate.opsForValue().size(key);
        } else if (type == DataType.LIST) {
            size = redisTemplate.opsForList().size(key);
            value = redisTemplate.opsForList().range(key, 0, -1);
        } else if (type == DataType.SET) {
            size = redisTemplate.opsForSet().size(key);
            value = redisTemplate.opsForSet().members(key);
        } else if (type == DataType.ZSET) {
            size = redisTemplate.opsForZSet().size(key);
            value = redisTemplate.opsForZSet().rangeWithScores(key, 0, -1);
        } else if (type == DataType.HASH) {
            size = redisTemplate.opsForHash().size(key);
            value = redisTemplate.opsForHash().entries(key);
        } else if (type == DataType.STREAM) {
            size = redisTemplate.opsForStream().size(key);
            value = redisTemplate.opsForStream().info(key);
        } else {
            return null;
        }
        Long expire = redisTemplate.getExpire(key);
        res.put("value", value);
        res.put("size", size);
        res.put("type", type.name().toLowerCase());
        res.put("ttl", expire);
        return res;
    }


    /**
     * 添加或修改
     */
    public Object addOrUpdate(KeyValuePojo keyValuePojo) {
        String key = keyValuePojo.getKey();
        boolean isUpdateAll = keyValuePojo.isUpdateAll();

        Long expire = null;
        // 若是全量更新，先删除旧的key
        if (isUpdateAll) {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
                // 记录ttl
                expire = redisTemplate.getExpire(key);
                redisTemplate.delete(key);
            }
        }

        DataType type = DataType.fromCode(keyValuePojo.getType());
        if (type == DataType.STRING) {
            redisTemplate.opsForValue().set(key, keyValuePojo.getNewValue());
        } else if (type == DataType.LIST) {
            if (isUpdateAll) {
                redisTemplate.opsForList().rightPushAll(key, keyValuePojo.getListValue());
            } else {
                // 修改list指定位置的值
                int index = keyValuePojo.getIndex();
                if (index == -1) {
                    //    末尾加
                    redisTemplate.opsForList().rightPush(key, keyValuePojo.getNewValue());
                } else if (index == -2) {
                    //    开头加
                    redisTemplate.opsForList().leftPush(key, keyValuePojo.getNewValue());
                } else {
                    // 指定index
                    redisTemplate.opsForList().set(key, index, keyValuePojo.getNewValue());
                }
            }
        } else if (type == DataType.SET) {
            if (isUpdateAll) {
                redisTemplate.opsForSet().add(key, collect2Arr(keyValuePojo.getListValue()));
            } else {
                // 移除旧值，新增新值
                String oldValue = keyValuePojo.getOldValue();
                redisTemplate.opsForSet().remove(key, oldValue);
                redisTemplate.opsForSet().add(key, keyValuePojo.getNewValue());
            }
        } else if (type == DataType.ZSET) {
            if (isUpdateAll) {
                Set<ZSetOperations.TypedTuple<String>> collect = keyValuePojo.getZsets()
                        .stream().map(zset -> new DefaultTypedTuple<>(zset.getValue(), zset.getScore())).collect(Collectors.toSet());
                redisTemplate.opsForZSet().add(key, collect);
            } else {
                // 单个值更新
                redisTemplate.opsForZSet().remove(key, keyValuePojo.getOldValue());
                redisTemplate.opsForZSet().add(key, keyValuePojo.getNewZset().getValue(), keyValuePojo.getNewZset().getScore());
            }
        } else if (type == DataType.HASH) {
            if (isUpdateAll) {
                redisTemplate.opsForHash().putAll(key, keyValuePojo.getMapValue());
            } else {
                redisTemplate.opsForHash().delete(key, keyValuePojo.getOldValue());
                redisTemplate.opsForHash().put(key, keyValuePojo.getNewValue(), keyValuePojo.getHashValue());
            }
        }
        // else if (type == DataType.STREAM) {
        //     redisTemplate.opsForStream().add(MapRecord.create(key, new HashMap<>()));
        // }
        // 重新设置 全量更新时的expire 值
        if (expire != null) {
            redisTemplate.expire(key, Duration.ofSeconds(expire));
        }
        return "OK";
    }

    /**
     * 设置ttl
     */
    public Object setTtl(KeyValuePojo keyValuePojo) {
        redisTemplate.expire(keyValuePojo.getKey(), keyValuePojo.getTtl(), TimeUnit.SECONDS);
        return "OK";
    }

    public Object delKeys(KeyValuePojo keyValuePojo) {
        if (keyValuePojo.isDelAll()) {
            redisTemplate.getRequiredConnectionFactory().getConnection().flushDb();
            return "OK";
        }
        Set<String> keys = keyValuePojo.getKeys();
        redisTemplate.delete(keys);
        return "OK";
    }

    public Object renameKey(KeyValuePojo keyValuePojo) {
        String srcKey = keyValuePojo.getSrcKey();
        String destKey = keyValuePojo.getDestKey();
        if (srcKey.equals(destKey)) {
            return "OK";
        }
        redisTemplate.rename(srcKey, destKey);
        return "OK";
    }

    public Object delValue(KeyValuePojo keyValuePojo) {
        String key = keyValuePojo.getKey();
        String subValue = keyValuePojo.getOldValue();

        DataType type = redisTemplate.type(key);
        if (type == DataType.STRING) {
            redisTemplate.opsForValue().set(key, "");
        } else if (type == DataType.LIST) {
            // 从前到后 删除一个 subValue 的元素
            redisTemplate.opsForList().remove(key, 1, subValue);
        } else if (type == DataType.SET) {
            redisTemplate.opsForSet().remove(key, subValue);
        } else if (type == DataType.ZSET) {
            redisTemplate.opsForZSet().remove(key, subValue);
        } else if (type == DataType.HASH) {
            redisTemplate.opsForHash().delete(key, subValue);
        }
        // else if (type == DataType.STREAM) {
        //    nothing
        // }
        else {
            return null;
        }
        return "OK";
    }

    /**
     * 将集合转换成数组
     */
    private String[] collect2Arr(Collection<String> collection) {
        String[] objects = new String[collection.size()];
        collection.toArray(objects);
        return objects;
    }
}
