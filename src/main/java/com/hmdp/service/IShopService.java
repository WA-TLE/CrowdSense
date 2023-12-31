package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author dy
 * @since 2023/10/16 10:06
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺信息
     *
     * @param id
     * @return
     */
    Result getShopById(Long id);

    /**
     * 根据 id 更新商铺信息
     *
     * @param shop
     * @return
     */
    Result updateShopById(Shop shop);

    void saveShopRedis(long l, long l1) throws InterruptedException;

    /**
     * 根据商铺类型分页查询商铺信息
     *
     * @param typeId
     * @param current
     * @param x
     * @param y
     * @return
     */
    Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
