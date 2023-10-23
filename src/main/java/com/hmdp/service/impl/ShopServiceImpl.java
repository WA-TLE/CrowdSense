package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author dy
 * @since 2023/10/16 10:06
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;


    //  线程池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 根据id查询商铺信息
     *
     * @param id
     * @return
     */
    public Result getShopById(Long id) {
        //  缓存穿透解决代码
//          Shop shop = queryWithPassThrough(id);

        //  使用互斥锁解决缓存击穿问题
//        Shop shop = queryWithMutex(id);

        //  使用逻辑删除解决缓存穿透问题
      //  Shop shop = queryWithLogicalExpire(id);

        //  封装缓存穿透代码
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return Result.ok(shop);
    }

    /**
     * 使用互斥锁解决缓存击穿问题
     *
     * @param id
     * @return
     */
    private Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;

        log.info("开始查询:");
        //  1. 从 Redis 中查询店铺信息
        String cacheShop = stringRedisTemplate.opsForValue().get(key);

        //  2. Redis 中存在店铺信息
        if (StrUtil.isNotBlank(cacheShop)) {
            return JSONUtil.toBean(cacheShop, Shop.class);
        }

        //  这里是为了应付缓存穿透
        if (cacheShop != null) {
            return null;
        }

        //  互斥锁的 key
        String lockKey = LOCK_SHOP_KEY + id;

        //  4. 未查询到结果, 从数据库中查询店铺
        //  4.1 尝试获取互斥锁
        Shop shop = null;
        try {
            boolean flag = tryLocal(lockKey);
            //  4.2 获取互斥锁失败, 休眠, 重新获取
            while (!flag) {
                log.info("获取互斥锁失败, 休眠, 重新获取");
                Thread.sleep(50);
                flag = tryLocal(lockKey);
            }

            //  4.3 获取互斥锁成功, 从数据库查询, 添加进缓存
            log.info("获取互斥锁成功, 从数据库查询, 添加进缓存");

            //  ** 这里应该再次查询缓存中是否有数据, 防止我们获取的锁是别的线程刚释放的 **
            //  而此时缓存中已经有过我们要的数据了
            cacheShop = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(cacheShop)) {
                log.info("缓存中已经存在我们要的数据了");
                return JSONUtil.toBean(cacheShop, Shop.class);
            }

            //  这里再次判断是否是缓存穿透问题
            if (cacheShop != null) {
                return null;
            }


            //  缓存中还没有数据, 从数据库查询
            shop = getById(id);
            Thread.sleep(200);  // TODO: 2023/10/22 这因为数据库在本地, 我们让它慢一点

            //  解决缓存穿透问题, 添加空值
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;  //  5. 数据库中没有结果, 返回异常信息
            }

            //  6. 查出店铺信息, 加入缓存
            String jsonShop = JSONUtil.toJsonStr(shop);
            //  加入 缓存超时时间
            stringRedisTemplate.opsForValue().set(key, jsonShop, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //  7. 释放互斥锁
            unlock(lockKey);
        }

        //  8. 返回数据给前端
        return shop;
    }


    /**
     * 使用逻辑过期时间来解决缓存击穿问题
     *
     * @param id
     * @return
     */
    private Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //  编写业务流程

        //  1. 从 Redis 中查询店铺信息
        String cacheShop = stringRedisTemplate.opsForValue().get(key);

        //  2. Redis 不存在店铺信息,
        if (StrUtil.isBlank(cacheShop)) {
            //  3. 数据有误, 直接返回 null
            //  这个业务常见一般用于商品的秒杀场景, 提前会把数据放入缓存的
            //  如果查询不到, 只能说明是查询的信息有误
            return null;
        }

        //  4. 命中, 需要将 json 反序列化为 Java 对象, 判断是否逻辑过期
        RedisData redisData = JSONUtil.toBean(cacheShop, RedisData.class);
        //  RedisData(expireTime=2023-10-22T20:15:59.685
        //  data={"area":"大关","openHours":"10:00-22:00","sold":4215,
        //  这里得到的已经是 json 格式了, 我们需要做的就是要把他强制转为 JSONObject 类型

        //  Data 我们设置的类型是 Object, 这里要先把它转为 JSONObject, 然后再转为 Shop
        //  再深入的话, 我就不明白了
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        //  5. 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {

            log.info("未过期, 返回店铺信息");
            //  5.1未过期, 直接返回店铺信息
            return shop;
        }


        //  5.2 过期
        //  6. 缓存重建
        //  6.1 尝试获取 互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLocal(lockKey);
        //  6.2 成功, 开启一个线程, 进行缓存重建
        // TODO: 2023/10/22 这里开启一个新线程不太明白, 是不明白怎么开启的
        if (isLock) {
            log.info("获取互斥锁成功, 进行缓存重建.....");
            CACHE_REBUILD_EXECUTOR.submit(() -> {

                try {
                    //重建缓存
                    log.info("开启新线程重建缓存");
                    this.saveShopRedis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }
        log.info("返回旧数据.....");
        //  6.3 成功, 未成功, 直接返回旧数据
        return shop;
    }


    /**
     * 缓存穿透解决代码, 未解决缓存击穿问题
     *
     * @param id
     * @return
     */
    private Shop queryWithPassThrough(Long id)  {
        String key = CACHE_SHOP_KEY + id;

        //  1. 从 Redis 中查询店铺信息
        String cacheShop = stringRedisTemplate.opsForValue().get(key);

        //  2. Redis 中存在店铺信息
        if (StrUtil.isNotBlank(cacheShop)) {
            //  3. 查询到结果, 直接返回
            return JSONUtil.toBean(cacheShop, Shop.class);
        }

        //  判断查询出的是否为 "", 这里的 "" 是我们故意往 Redis 中放入的!!!
        //  因为上面已经判断过是否有值了, 现在就剩两种情况, 要么为 null, 要么 是 ""
        if (cacheShop != null) {
            //  这个就是为了应付缓存穿透
            return null;
        }


        //  4. 未查询到结果, 从数据库中查询店铺
        Shop shop = getById(id);

//        try {
//            Thread.sleep(200);
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }


        if (shop == null) {

            //  解决缓存穿透问题, 添加空值                  //  过期时间可以设置的快一点
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //  5. 数据库中没有结果, 返回异常信息
            return null;
        }

        //  6. 查出店铺信息, 加入缓存
        String jsonShop = JSONUtil.toJsonStr(shop);

        //  加入 缓存超时时间
        stringRedisTemplate.opsForValue().set(key, jsonShop, CACHE_SHOP_TTL, TimeUnit.MINUTES);


        //  7. 返回数据给前端
        return shop;
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

    /**
     * 根据 id 更新商铺信息
     *
     * @param shop
     * @return
     */
    @Transactional
    public Result updateShopById(Shop shop) {

        //  0. 判断店铺 id 是否存在
        Long id = shop.getId();
        if (id == null) {
            Result.fail("店铺信息有误, 请重新操作");
        }

        //  店铺缓存对应 key
        String key = CACHE_SHOP_KEY + id;

        //  1. 更新数据库
        updateById(shop);

        //  2. 删除对应缓存
        stringRedisTemplate.delete(key);

        return Result.ok();
    }

    /**
     * 给缓存中添加数据的逻辑过期时间
     *
     * @param id
     * @param expireSecond
     */
    @Override
    public void saveShopRedis(long id, long expireSecond) throws InterruptedException {
        //  查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);


        //  封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);        // TODO: 2023/10/23 逻辑过期时间, 这里应该更改一下
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond));

        //  写入 Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }


}
