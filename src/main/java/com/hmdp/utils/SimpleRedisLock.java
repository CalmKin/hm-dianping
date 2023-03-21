package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{


    public static final String KEY_PREFIX = "lock:";    //锁的前缀，可以写死
    private StringRedisTemplate redisTemplate;

    private String name;    //业务名称，因为不同的业务拥有不同的锁

    public static final String VAL_PREFIX = UUID.randomUUID().toString(true);   //因为集群下线程id可能重复，所以用uuid区分不同结点


    //因为不是spring管理的，所以只能通过构造函数的方式进行初始化
    public SimpleRedisLock(StringRedisTemplate redisTemplate, String name) {
        this.redisTemplate = redisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(long timesec) {
        //获取当前线程的id，防止释放锁的时候误操作
        long id = Thread.currentThread().getId();
        Boolean success = redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, VAL_PREFIX + id, timesec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    @Override
    public void unlock() {

        //获取当前锁的值
        String s = redisTemplate.opsForValue().get(KEY_PREFIX + name);

        String curId = VAL_PREFIX + Thread.currentThread().toString();

        if(curId.equals(s))
        {
            redisTemplate.delete(KEY_PREFIX+name);
        }

    }
}
