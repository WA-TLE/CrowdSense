package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author dy
 * @since 2023/10/16 10:06
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    /**
     * 优惠卷秒杀
     *
     * @param voucherId
     * @return
     */
    @Transactional
    public Result seckillVoucher(Long voucherId) {

        //  1.  根据 id 查询优惠卷信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //  2. 判断日期是否符合要求

        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        if (beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("活动尚未开始");
        }

        LocalDateTime endTime = seckillVoucher.getEndTime();
        if (endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("活动已经结束!");
        }

        //  3. 库存是否充足
        Integer stock = seckillVoucher.getStock();
        if (stock < 1) {
            return Result.fail("库存不足!");
        }

        //  4. 扣减库存
        boolean success = seckillVoucherService
                .update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();

        if (!success) {
            return Result.fail("库存不足");
        }

        //  5. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();

        //  5.1 获取 id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);

        //  5.2 用户 id
        Long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);

        // 5.3 代金券 id
        voucherOrder.setVoucherId(voucherId);

        //  5. 写入数据库
        save(voucherOrder);

        //  6. 返回数据


        return Result.ok(orderId);
    }
}
