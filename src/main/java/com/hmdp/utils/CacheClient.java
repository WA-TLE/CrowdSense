package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    //  线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);



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


    /**
     * 使用逻辑过期解决缓存击穿问题
     *
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param unit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        //  1. 从 Redis 中查询店铺信息
        String cacheShop = stringRedisTemplate.opsForValue().get(key);

        //  2. Redis 不存在店铺信息, 这个适用于秒杀, 我们一般提前放入商品并且添加逻辑过期时间
        if (StrUtil.isBlank(cacheShop)) {
            return null;
        }

        //  4. 命中, 需要将 json 反序列化为 Java 对象, 判断是否逻辑过期
        RedisData redisData = JSONUtil.toBean(cacheShop, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data, type);

        LocalDateTime expireTime = redisData.getExpireTime();

        //  5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            log.info("未过期, 返回店铺信息");
            return r;
        }


        //  5.2 过期
        //  6. 缓存重建
        //  6.1 尝试获取 互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLocal(lockKey);
        //  6.2 成功, 开启一个线程, 进行缓存重建



        if (isLock) {
            log.info("获取互斥锁成功, 进行缓存重建.....");
            CACHE_REBUILD_EXECUTOR.submit(() -> {

                try {
                    log.info("开启新线程重建缓存");

                    Thread.sleep(100);

                    //  从数据库查询
                    R newR = dbFallback.apply(id);
                    //  重建缓存
                    this.setWithLogicalExpire(key, newR, time, unit);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        log.info("返回旧数据.....");
        //  6.3 成功, 未成功, 直接返回旧数据
        return r;
    }

    //  获取互斥锁
    private boolean tryLocal(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);

        //  这里不能直接返回 flag, 将 Boolean 直接返回的话, 是会做拆箱的, 这样可能会出现空指针
        return BooleanUtil.isTrue(flag);
    }

    //  释放互斥锁
    private void unlock(String key) {
        //  这里直接删除即可, 无需返回值
        stringRedisTemplate.delete(key);
    }


}
