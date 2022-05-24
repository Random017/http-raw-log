package com.skycong.redisweb.service;

import com.skycong.redisweb.pojo.KeyValuePojo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.DataType;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.types.RedisClientInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
        RedisConnection connection = redisTemplate.getRequiredConnectionFactory().getConnection();
        Long expire = redisTemplate.getExpire(key);

        HashMap<String, Object> map = new HashMap<>(2);
        Object v = null;
        long size;
        DataType type = redisTemplate.type(key);
        if (type == DataType.STRING) {
            v = redisTemplate.opsForValue().get(key);
            size = redisTemplate.opsForValue().size(key);
        } else if (type == DataType.LIST) {
            size = redisTemplate.opsForList().size(key);
            Long lLen = connection.lLen(key.getBytes(StandardCharsets.UTF_8));
            if (lLen != null && lLen > 0) {
                v = redisTemplate.opsForList().range(key, 0, lLen - 1);
            }
        } else if (type == DataType.SET) {
            size = redisTemplate.opsForSet().size(key);
            Cursor<String> scan = redisTemplate.opsForSet().scan(key, ScanOptions.scanOptions().match("*").count(10000).build());
            Set<String> sets = new HashSet<>(128);
            while (scan.hasNext()) {
                sets.add(scan.next());
            }
            try {
                scan.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            v = sets;
        } else if (type == DataType.ZSET) {
            size = redisTemplate.opsForZSet().size(key);
            Long lLen = redisTemplate.opsForZSet().zCard(key);
            if (lLen != null && lLen > 0) {
                v = redisTemplate.opsForZSet().rangeWithScores(key, 0, lLen - 1);
            }
        } else if (type == DataType.HASH) {
            size = redisTemplate.opsForHash().size(key);
            v = redisTemplate.opsForHash().entries(key);
        } else if (type == DataType.STREAM) {
            size = redisTemplate.opsForStream().size(key);
            v = redisTemplate.opsForStream().info(key);
        } else {
            return null;
        }
        map.put("value", v);
        map.put("size", size);
        map.put("type", type.name().toLowerCase());
        map.put("ttl", expire);
        connection.close();
        return map;
    }


    /**
     * 添加或修改
     */
    public Object addKey(KeyValuePojo keyValuePojo) {
        RedisConnection connection = redisTemplate.getRequiredConnectionFactory().getConnection();
        String key = keyValuePojo.getKey();

        DataType type = DataType.fromCode(keyValuePojo.getType());
        if (type == DataType.STRING) {
            redisTemplate.opsForValue().set(key, keyValuePojo.getNewValue());
        } else if (type == DataType.LIST) {
            if (keyValuePojo.isUpdateAll()) {
                // 先清空list
                redisTemplate.opsForList().trim(key, 1, 0);
                // 再加入
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
                    connection.lSet(key.getBytes(StandardCharsets.UTF_8), index, keyValuePojo.getNewValue().getBytes(StandardCharsets.UTF_8));
                }
            }
        } else if (type == DataType.SET) {
            if (keyValuePojo.isUpdateAll()) {
                Set<String> members = redisTemplate.opsForSet().members(key);
                // 先清空
                if (members.size() > 0) {
                    Object[] objects = new Object[members.size()];
                    members.toArray(objects);
                    redisTemplate.opsForSet().remove(key, objects);
                }
                // 再添加
                List<String> listValue = keyValuePojo.getListValue();
                String[] strings = new String[listValue.size()];
                listValue.toArray(strings);
                redisTemplate.opsForSet().add(key, strings);
            } else {
                // 移除旧值，新增新值
                String oldValue = keyValuePojo.getOldValue();
                redisTemplate.opsForSet().remove(key, oldValue);
                redisTemplate.opsForSet().add(key, keyValuePojo.getNewValue());
            }
        } else if (type == DataType.ZSET) {
            if (keyValuePojo.isUpdateAll()) {
                Boolean hasKey = redisTemplate.hasKey(key);
                // 先删除旧的
                if (Boolean.TRUE.equals(hasKey)) {
                    Set<String> allValues = redisTemplate.opsForZSet().range(key, 0, -1);
                    Object[] objects = new Object[allValues.size()];
                    allValues.toArray(objects);
                    redisTemplate.opsForZSet().remove(key, objects);
                }
                // 保存新的
                Set<ZSetOperations.TypedTuple<String>> collect = keyValuePojo.getZsets().stream().map(zset -> new DefaultTypedTuple<>(zset.getValue(), zset.getScore())).collect(Collectors.toSet());
                redisTemplate.opsForZSet().add(key, collect);
            } else {
                // 单个值更新
                redisTemplate.opsForZSet().remove(key, keyValuePojo.getOldValue());
                redisTemplate.opsForZSet().add(key, keyValuePojo.getNewZset().getValue(), keyValuePojo.getNewZset().getScore());
            }
        } else if (type == DataType.HASH) {
            if (keyValuePojo.isUpdateAll()) {
                redisTemplate.opsForHash().putAll(key, keyValuePojo.getMapValue());
            } else {
                redisTemplate.opsForHash().delete(key, keyValuePojo.getOldValue());
                redisTemplate.opsForHash().put(key, keyValuePojo.getNewValue(), keyValuePojo.getHashValue());
            }
        } else if (type == DataType.STREAM) {
            redisTemplate.opsForStream().add(MapRecord.create(key, new HashMap<>()));
        }
        connection.close();
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
        } else if (type == DataType.STREAM) {
        } else {
            return null;
        }
        return "OK";
    }
}
