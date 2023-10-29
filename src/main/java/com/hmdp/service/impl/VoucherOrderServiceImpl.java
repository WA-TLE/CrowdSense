package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;



    /*
    * lua 脚本需要用到的东西
    * */
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /*
        创建阻塞队列
     */
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    //异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    private IVoucherOrderService proxy ;

    //在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }


    /*
        创建匿名内部类, 让他实现 Runnable (线程任务), 线程池执行这里的业务
        只要阻塞队列里面有东西, 这个类中的 run 方法就要执行, 所以要让这个类一初始化就来执行这个任务

          怎么做?  Spring 提供的注解 @PostConstruct 当前类初始化完成后来执行

     */
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                //  1. 获取阻塞队列中的元素
                try {
                    VoucherOrder voucherOrder = orderTasks.take();
                    //  处理订单业务
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("创建订单出现异常: ", e);
                }
                //  2. 创建订单
            }
        }
    }

    /**
     * 处理订单业务
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {

        //  获取用户 id, 用于加锁   todo
        Long userId = voucherOrder.getUserId();

//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean isLock = lock.tryLock();

        if (!isLock) {
           log.error("不能重复下单");
        }
        /*
            createVoucherOrder(voucherId)
            this.createVoucherOrder(voucherId)
            其实是this.的方式调用的，事务想要生效，
            还得利用代理来生效，所以这个地方，我们需要获得原始的事务对象，
            来操作事务获取原始的事务对象来操作事务
        */
        try {
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }

    }

    /**
     *
     * 优惠卷秒杀
     *
     * @param voucherId
     * @return
     */

    public Result seckillVoucher(Long voucherId) {

        //  获取用户
        Long userId = UserHolder.getUser().getId();

        //  1. 执行 lua 脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );

        int r = result.intValue();

        //  判断是否为 0
        if (r != 0) {
            //  代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单哦~");
        }

        // TODO: 2023/10/28 保存到阻塞队列

        //   2.1 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();

        //   2.2 获取本次订单的  id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);

        //   2.3 用户 id
        voucherOrder.setUserId(userId);

        //  2.4 代金券 id
        voucherOrder.setVoucherId(voucherId);
        //  2.5 这里需要把上面封装的订单数据添加到阻塞队列中
        orderTasks.add(voucherOrder);

        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //  返回订单 id
        return Result.ok(orderId);

    }

    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 5.1.查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            log.error("用户已经购买过了");
            return ;
        }

        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            // 扣减失败
            log.error("库存不足");
            return ;
        }
        save(voucherOrder);
    }

}
