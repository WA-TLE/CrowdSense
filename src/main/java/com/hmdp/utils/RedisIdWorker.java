package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @Author: dy
 * @Date: 2023/10/23 20:51
 * @Description: 全局 id 生成器
 */

@Component
public class RedisIdWorker {

    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    /**
     * 序列号位数
     */
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 我们的 id 生成策略为 时间戳 + Redis key 的自增 id
     *
     * @param keyPrefix
     * @return
     */
    public long nextId(String keyPrefix) {

        //  1. 生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        //  2. 生成序列号
        //  2.1 获取当天日期, 精确到天
        String data = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));

        //  2.2 自增长, 这里是让 Redis 和 key 对应的那个 value 自增长, 并且把它返回
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + data + ":");

        //  3. 拼接并返回

        //  这里的 count 并不会出现 null, 因为就算是到了当天的第一个订单, 我们 Redis 自增 id, 最初是 1
        //  2023-10-29 20:52:50 补充: 最初创建 key 的时候, 对应的 value 是 1
        return timestamp << COUNT_BITS | count;
    }

}
