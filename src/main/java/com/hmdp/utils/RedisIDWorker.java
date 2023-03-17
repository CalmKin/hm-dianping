package com.hmdp.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDWorker {
    /**
     * 计算基准时间
     * LocalDateTime now = LocalDateTime.of(2022,1,1,0,0,0);
     *         long cur = now.toEpochSecond(ZoneOffset.UTC);
     *         System.out.println(cur);
     */


    //基准时间对应的秒数
    private static long BASE_TIME_SEC = 1640995200;
    //时间戳相对与订单序列号的位移
    private static int BIT_MOVE = 32;

    @Autowired
    private StringRedisTemplate redisTemplate;

    public long nextId(String prefix)   //传入业务前缀构造key
    {

        LocalDateTime now = LocalDateTime.now();
        long curSec = now.toEpochSecond(ZoneOffset.UTC);
        //当前时间与基准时间的差值
        long gap = curSec - BASE_TIME_SEC;
        String timeFormat = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        String key = "incr:" + prefix + ":" + timeFormat ;

        //获取redis数据库自增的id（真正存储的id）
        long id = redisTemplate.opsForValue().increment(key);   //如果key不存在，redis会自动创建，不会报空指针异常

        return gap<<BIT_MOVE | id;  //展示给用户看的id
    }

}
