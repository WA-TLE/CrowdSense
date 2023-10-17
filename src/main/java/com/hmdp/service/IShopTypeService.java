package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author dy
 * @since 2023/10/16 10:06
 */
public interface IShopTypeService extends IService<ShopType> {

    /**
     * 查询店铺类型列表
     *
     * @return
     */
    Result queryTypeList();
}
