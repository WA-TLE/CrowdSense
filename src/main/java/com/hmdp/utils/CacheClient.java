package com.hmdp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @Author: dy
 * @Date: 2023/10/22 21:35
 * @Description:
 */
@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 设置 TTL 过期时间
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        //  这里将 Object 对象转化为 JSON 格式
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 设置逻辑过期时间
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //  设置逻辑过期时间, 这里咱们要新 new 一个 RedisData 对象, 来封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        //  将时间的单位转化
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));

        //  写入 Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));

    }


    /**
     * 封装缓存穿透问题代码
     *
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        //  1. 从 Redis 中查询店铺信息
        String json = stringRedisTemplate.opsForValue().get(key);

        //  2. Redis 中存在店铺信息
        if (StrUtil.isNotBlank(json)) {
            //  3. 查询到结果, 直接返回
            return JSONUtil.toBean(json, type);
        }

        //  不为 null 的话, 那么就是 "", 说明这个数据在数据库中不存在, 直接返回错误信息
        if (json != null) {
            return null;
        }

        //  4. 未查询到结果, 从数据库中查询店铺
        R r = dbFallback.apply(id);

        if (r == null) {

            //  解决缓存穿透问题, 添加空值                  //  过期时间可以设置的快一点
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //  5. 数据库中没有结果, 返回异常信息
            return null;
        }

        //  数据库存在数据, 将数据加入缓存
        this.set(key, r, time, unit);

        //  7. 返回数据给前端
        return r;
    }


}
