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
}
