package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author dy
 * @since 2023/10/16 10:06
 */
@Service
@Slf4j
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询店铺类型列表
     *
     * @return
     */
    public Result queryTypeList() {


        String key = SHOP_TYPE_KEY;
        //  编写业务流程

        //  1. 从 Redis 中查询店铺类型列表信息
        String cacheShopList = stringRedisTemplate.opsForValue().get(key);

        //  2. 查询出店铺类型列表
        if (StrUtil.isNotBlank(cacheShopList)) {

            List<ShopType> shopTypes = JSONUtil.toList(cacheShopList, ShopType.class);
            log.info("从 Redis 中查询出店铺类型列表: {}", shopTypes);

            //  3. 查询到结果, 直接返回
            return Result.ok(shopTypes);
        }

        List<ShopType> shopTypeList = query().orderByAsc("sort").list();


        if (shopTypeList == null) {
            //  5. 数据库中没有结果, 返回异常信息
            return Result.fail("网络异常, 请稍后尝试");
        }

        //  6. 查出店铺信息, 加入缓存
        String jsonShopList = JSONUtil.toJsonStr(shopTypeList);

        log.info("加入缓存的店铺类型列表信息: {}", jsonShopList);

        stringRedisTemplate.opsForValue().set(key, jsonShopList);


        // TODO: 2023/10/22 这里应该返回店铺类型信息
        return null;
    }
}
