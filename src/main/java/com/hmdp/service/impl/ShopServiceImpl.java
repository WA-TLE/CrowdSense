package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

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
        Shop shop = queryWithMutex(id);


        return Result.ok(shop);
    }

    private Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //  编写业务流程

        //  1. 从 Redis 中查询店铺信息
        String cacheShop = stringRedisTemplate.opsForValue().get(key);

        //  2. Redis 中存在店铺信息
        if (StrUtil.isNotBlank(cacheShop)) {
            return JSONUtil.toBean(cacheShop, Shop.class);
        }

        //  判断查询出的是否为 ""
        //  要么为 null, 要么 是 ""
        if (cacheShop != null) {
            return null;
        }

        String lockKey = LOCK_SHOP_KEY + id;

        //  4. 未查询到结果, 从数据库中查询店铺
        //  4.1 尝试获取互斥锁
        Shop shop = null;
        try {
            boolean flag = tryLocal(lockKey);
            //  4.2 获取互斥锁失败, 休眠, 重新获取
            while (!flag) {
                Thread.sleep(50);
                flag = tryLocal(lockKey);
            }

            //  4.3 获取互斥锁成功, 从数据库查询, 添加进缓存


            //  这里应该再次查询缓存中是否有数据, 防止我们获取的锁是别的线程刚释放的
            //  而此时缓存中已经有过我们要的数据了
            cacheShop = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(cacheShop)) {
                return JSONUtil.toBean(cacheShop, Shop.class);
            }
            //  缓存中还没有数据, 从数据库查询
            shop = getById(id);
            Thread.sleep(200);

            if (shop == null) {
                //  解决缓存穿透问题, 添加空值
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                //  5. 数据库中没有结果, 返回异常信息
                return null;
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
     * 缓存穿透解决代码, 未解决缓存击穿问题
     *
     * @param id
     * @return
     */
    private Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //  编写业务流程

        //  1. 从 Redis 中查询店铺信息
        String cacheShop = stringRedisTemplate.opsForValue().get(key);

        //  2. Redis 中存在店铺信息
        if (StrUtil.isNotBlank(cacheShop)) {
            Shop shopBean = JSONUtil.toBean(cacheShop, Shop.class);
            log.info("从 Redis 中查询出店铺信息: {}", cacheShop);
            //  3. 查询到结果, 直接返回
            return shopBean;
        }

        //  判断查询出的是否为 ""
        //  因为上面已经判断过是否有值了, 现在就剩两种情况
        //  要么为 null, 要么 是 ""
        if (cacheShop != null) {
            return null;
        }


        //  4. 未查询到结果, 从数据库中查询店铺
        Shop shop = getById(id);


        if (shop == null) {

            //  解决缓存穿透问题, 添加空值
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
}
