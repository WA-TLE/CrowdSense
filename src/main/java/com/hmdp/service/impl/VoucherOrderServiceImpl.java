package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 优惠卷秒杀
     *
     * @param voucherId
     * @return
     */

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

        //  获取用户 id, 用于加锁
        Long userId = UserHolder.getUser().getId();

//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean isLock = lock.tryLock();

        if (!isLock) {
            return Result.fail("不能重复下单哦~");
        }
        /*
            createVoucherOrder(voucherId)
            this.createVoucherOrder(voucherId)
            其实是this.的方式调用的，事务想要生效，
            还得利用代理来生效，所以这个地方，我们需要获得原始的事务对象，
            来操作事务获取原始的事务对象来操作事务
        */
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //  4. 判断用户是否重复领取优惠卷
        Long userId = UserHolder.getUser().getId();


        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();

        if (count > 0) {
            return Result.fail("您已经领取过优惠卷(*^▽^*)");
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

        //  5.1 获取本次订单的  id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);

        //  5.2 用户 id
        voucherOrder.setUserId(userId);

        // 5.3 代金券 id
        voucherOrder.setVoucherId(voucherId);

        //  5. 写入数据库
        save(voucherOrder);

        //  6. 返回数据
        return Result.ok(orderId);
    }
}
