package com.hmdp.utils;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * ClassName: RedisIdWorker
 * Package: com.hmdp.utils
 * Description:
 *
 * @Author Chao Fang
 * @Create 2024/4/19 13:39
 * @Version 1.0
 */
@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1672531200L;
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate redisTemplate;

    public RedisIdWorker(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Long nextId(String keyPrefix) {
        LocalDateTime now = LocalDateTime.now();
        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = redisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        return timestamp << COUNT_BITS | count;
    }

}
