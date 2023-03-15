package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * 解决缓存击穿问题
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);

        Shop shop = queryWithMutex(id);

        if(shop == null)
        {
            return Result.fail("商品信息不存在");
        }
        return Result.ok();
    }


    /**
     * 解决缓存击穿问题
     *
     * @param id
     * @return
     */
    @Override
    public Shop     queryWithMutex(Long id) {

        //先查询redis
        String s = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        Shop shop = null;

        //如果redis里面存在店铺数据，将字符串转换成对象返回
        if(!StrUtil.isBlank(s))     //也会排除""的情况
        {
            shop = JSON.parseObject(s, Shop.class);
            return shop;
        }

        if( s != null ) //如果没有进去上面那种情况，那么就是 ""了
        {
            return null;
        }

        //实现缓存重建

        //获取互斥锁
        String key = "lock:shop" + id;

        boolean b = tryLock(key);
        try {
            if(!b)
            {
                //获取失败，休眠并重试,重试的话就是从头执行函数，也就相当于递归调用函数
                Thread.sleep(50);
                return queryWithMutex(id);
            }

            shop  = this.getById(id);

            //如果数据不存在，缓存空值，然后再返回
            if(shop == null)
            {
                //空值的有效期不要设置那么长
                redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            //否则先将数据存进redis中
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSON.toJSONString(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //无论如何，最后都要释放互斥锁
            release(key);
        }
        return shop;
    }

    /**
     * 缓存店铺数据,解决缓存穿透问题
     *
     * @param id
     * @return
     */
    @Override
    public Shop queryWithPassThrough(Long id) {

        //先查询redis
        String s = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        Shop shop = null;

        //如果redis里面存在店铺数据，将字符串转换成对象返回
        if(!StrUtil.isBlank(s))     //也会排除""的情况
        {
            shop = JSON.parseObject(s, Shop.class);
            return shop;
        }

        if( s != null ) //如果没有进去上面那种情况，那么就是 ""了
        {
            return null;
        }

        //否则查询mysql数据库
        shop  = this.getById(id);

        //如果数据不存在，缓存空值，然后再返回
        if(shop == null)
        {
            //空值的有效期不要设置那么长
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }

        //否则先将数据存进redis中
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSON.toJSONString(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //然后将数据返回用户
        return shop;
    }

    /**
     * 更新店铺信息操作
     * 先操作数据库再删除缓存
     * 要保证对数据库的操作和对缓存操作是一个原子操作
     * @param shop
     * @return
     */
    @Transactional
    @Override
    public Result updateShop(Shop shop) {

        //获取shop的id，要保证id不为空才能完成更新操作
        Long id = shop.getId();

        if(id==null)
        {
            return Result.fail("店铺id不能为空");
        }

        //先更新数据库
        this.updateById(shop);
        //再更新缓存
        redisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
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
