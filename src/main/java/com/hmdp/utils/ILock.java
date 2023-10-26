package com.hmdp.utils;

/**
 * @Author: dy
 * @Date: 2023/10/26 19:17
 * @Description:
 */
public interface ILock {
    /**
     * 尝试获取锁
     *
     * @param timeoutSec
     * @return
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unlock();

}
