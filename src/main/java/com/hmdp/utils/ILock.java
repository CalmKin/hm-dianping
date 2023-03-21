package com.hmdp.utils;

public interface ILock {

    /**
     * 尝试获取锁
     * @param timesec 锁持有的超时时间，过期后自动失效
     * @return true获取成功
     */
    boolean tryLock(long timesec);

    /**
     * 释放锁
     */
    void unlock();

}
