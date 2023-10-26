package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author dy
 * @since 2023/10/16 10:06
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    /**
     * 优惠卷秒杀
     *
     * @param voucherId
     * @return
     */
    Result seckillVoucher(Long voucherId);

    Result getResult(Long voucherId);
}
