package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.ssh.JschUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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

        //  4. 未查询到结果, 从数据库中查询店铺
        Shop shop = getById(id);


        if (shop == null) {
            //  5. 数据库中没有结果, 返回异常信息
            return Result.fail("您所查询的店铺不存在");
        }

        //  6. 查出店铺信息, 加入缓存
        String jsonShop = JSONUtil.toJsonStr(shop);

        stringRedisTemplate.opsForValue().set(key, jsonShop);


        //  7. 返回数据给前端
        return Result.ok(shop);
    }
}
