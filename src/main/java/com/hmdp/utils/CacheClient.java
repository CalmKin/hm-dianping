package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * 封装redis工具类
 */
@Component
@Slf4j
public class CacheClient {

    @Autowired
    private StringRedisTemplate redisTemplate;

    //将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key,Object o, Long duration, TimeUnit unit)
    {
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(o),duration,unit);
    }

    // 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public void setWithLogicalExpire( String key,Object o , Long duration , TimeUnit unit  )
    {
        RedisData redisData = new RedisData();
        redisData.setData(o);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(duration)));     //因为传进来的时间单元可能不同，所以统一转换成秒为单位
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }



    //根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    //返回值类型和id类型都不确定，所以都用泛型来指定
    //因为要把json字符串转化为对象，要用到字节码，所以参数里面要传
    //因为要查数据库，所以参数要传函数
    //过期时间也要通过参数动态传入
    public <R,ID> R queryWithPassThrough(String prefix, ID id, Class<R> rClass, Function<ID,R> function ,Long duration,TimeUnit unit) {

        //先查询redis
        String key =prefix + id;
        String s = redisTemplate.opsForValue().get(key);

        R ret = null;

        //如果redis里面存在店铺数据，将字符串转换成对象返回
        if(StrUtil.isNotBlank(s))     //也会排除""的情况
        {
            ret = JSON.parseObject(s, rClass );
            return ret;
        }

        if( s != null ) //如果没有进去上面那种情况，那么就是 ""了
        {
            return null;
        }

        System.out.println("可以查询到店铺");

        //否则查询mysql数据库
        ret = function.apply(id);
        //如果数据不存在，缓存空值，然后再返回
        if(ret == null)
        {
            //将空值写入redis
            // 空值的有效期不要设置那么长
            redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        //否则先将数据存进redis中
        this.set(key,ret,duration,unit);
        //然后将数据返回用户
        return ret;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    //根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
    public <R,ID> R queryWithLogicalExpire(String prefix,String lockPrefix,ID id,Class<R> rClass,Function<ID,R> function,Long duration,TimeUnit unit) {

        //先查询redis
        String key = prefix + id;
        String s = redisTemplate.opsForValue().get(key);

        R ret = null;

        //缓存不存在，直接返回null
        if(StrUtil.isBlank(s))
        {
            return null;
        }

        //缓存存在
        RedisData redisData = JSONUtil.toBean(s,RedisData.class);
        //因为redisdata里面的data类型是object
        //所以data类型是JSONObject，不能直接强转为shop
        ret=JSONUtil.toBean((JSONObject) redisData.getData(),rClass);

        //缓存没有过期=》直接返回
        if( redisData.getExpireTime().isAfter( LocalDateTime.now() ) )
        {
            return ret;
        }
        //缓存过期，缓存重建

        //获取互斥锁  TODO 互斥锁的key需要换一下
        String lockKey = lockPrefix+id;
        boolean isLock = tryLock(lockKey);
        //成功，新开一个线程去查询数据库
        if(isLock)
        {
            //新创建一个线程，执行查询任务
            CACHE_REBUILD_EXECUTOR.submit(()->{

                //重建缓存
                try {
                    R apply = function.apply(id);
                    this.setWithLogicalExpire(prefix+id,apply,duration,unit);
                }catch (Exception e){
                    throw  new RuntimeException();
                }finally {
                    //最终都要释放锁
                    release(lockKey);
                }


            });
        }
        //不管成功还是失败，都直接返回shop
        return ret;
    }


    /**
     * 用互斥锁解决缓存击穿问题
     *
     * @param id
     * @return
     */
    public <R,ID> R  queryWithMutex(String prefix,String lockPreefix,ID id,Class<R> rClass , Function<ID,R> function,Long duration,TimeUnit unit) {

        //先查询redis
        String key = prefix + id;
        String s = redisTemplate.opsForValue().get(key);

        R ret = null;

        //如果redis里面存在店铺数据，将字符串转换成对象返回
        if(StrUtil.isNotBlank(s))     //也会排除""的情况
        {
            ret = JSON.parseObject(s, rClass);
            return ret;
        }

        if( s != null ) //如果没有进去上面那种情况，那么就是 ""了
        {
            return null;
        }

        //实现缓存重建

        //获取互斥锁
        String lockKey =lockPreefix+ id;

        boolean b = tryLock(lockKey);
        try {
            if(!b)
            {
                //获取失败，休眠并重试,重试的话就是从头执行函数，也就相当于递归调用函数
                Thread.sleep(50);
                return queryWithMutex(prefix,lockPreefix,id,rClass,function,duration,unit);
            }

            //获取锁成功，查询数据库
            ret = function.apply(id);

            //如果数据不存在，缓存空值，然后再返回
            if(ret == null)
            {
                //空值的有效期不要设置那么长
                redisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            //否则先将数据存进redis中
            redisTemplate.opsForValue().set(key,JSON.toJSONString(ret),unit.toSeconds(duration),TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //无论如何，最后都要释放互斥锁
            release(lockKey);
        }
        return ret;
    }




    /**
     * 利用redis的retnx操作来实现锁机制
     * 获取锁的函数，key是店铺的标识，也就是说给每个店铺都设置了一个锁
     * @param key
     * @return
     */
    private boolean tryLock(String key)
    {
        Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);//过时时间一般设置为业务时间的两倍左右
        return BooleanUtil.isTrue(success);     //因为拆箱过程会有空值
    }

    /**
     * 释放锁的函数
     * @param key
     */
    void release(String key)
    {
        redisTemplate.delete(key);
    }

}
