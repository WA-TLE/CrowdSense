package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @Author: dy
 * @Date: 2023/10/26 19:19
 * @Description:
 */
public class SimpleRedisLock implements ILock {

    /**
     * 业务名称
     */
    private String name;
    /**
     * Redis 中的前缀
     */
    private static final String KEY_PREFIX = "lock:";

    /**
     * redis 客户端
     */
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Redis 中的 value, 防止误删锁问题
     */
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) +"-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 尝试获取锁
     *
     * @param timeoutSec
     * @return
     */
    public boolean tryLock(long timeoutSec) {
        //  获取线程的标识, 用作 value, 表明是哪个线程拿到的锁
        String  threadId = ID_PREFIX + Thread.currentThread().getId();
        //  获取锁
        Boolean success = stringRedisTemplate
                .opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);

        //  防止自动拆箱出现空指针异常
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     *
     */
    public void unlock() {

        // 调用lua脚本
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,          //  Collections.singletonList 将字符串转化为单一集合
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());


    }

   /*
    释放锁
    public void unlock() {

        //  获取原本应该有的 value
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        //  取出对应的 value
        String value = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        if (threadId.equals(value)) {
            //  删除锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }

        //  否则的话, 说明自己的锁已经被释放了, 我们什么都不用做


    }*/
}
