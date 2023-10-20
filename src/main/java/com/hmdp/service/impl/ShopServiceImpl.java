package com.hmdp.service.impl;

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
        String key = CACHE_SHOP_KEY + id;
        //  编写业务流程

        //  1. 从 Redis 中查询店铺信息
        String cacheShop = stringRedisTemplate.opsForValue().get(key);

        //  2. 查询出店铺信息
        if (StrUtil.isNotBlank(cacheShop)) {
            Shop shopBean = JSONUtil.toBean(cacheShop, Shop.class);
            log.info("从 Redis 中查询出店铺信息: {}", cacheShop);
            //  3. 查询到结果, 直接返回
            return Result.ok(shopBean);
        }

        //  判断查询出的是否为 ""
        //  因为上面已经判断过是否有值了, 现在就剩两种情况
        //  要么为 null, 要么 是 ""
        if (cacheShop != null) {
            return Result.fail("店铺不存在~");
        }


        //  4. 未查询到结果, 从数据库中查询店铺
        Shop shop = getById(id);


        if (shop == null) {

            //  解决缓存穿透问题, 添加空值
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //  5. 数据库中没有结果, 返回异常信息
            return Result.fail("您所查询的店铺不存在");
        }

        //  6. 查出店铺信息, 加入缓存
        String jsonShop = JSONUtil.toJsonStr(shop);

        //  加入 缓存超时时间
        stringRedisTemplate.opsForValue().set(key, jsonShop, CACHE_SHOP_TTL, TimeUnit.MINUTES);


        //  7. 返回数据给前端
        return Result.ok(shop);
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
