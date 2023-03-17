package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

        //用互斥锁解决缓存击穿问题
        //Shop shop = queryWithMutex(id);

        //用逻辑过期时间解决缓存击穿问题
        Shop shop = queryWithLogicalExpire(id);

        if(shop == null)
        {
            return Result.fail("商品信息不存在");
        }
        return Result.ok();
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    /**
     * 用逻辑过期时间解决缓存击穿的问题
     *
     * @param id
     * @return
     */
    @Override
    public Shop queryWithLogicalExpire(Long id) {

        //先查询redis
        String s = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        Shop shop = null;

        //缓存不存在，直接返回null
        if(s==null || StrUtil.isBlank(s))
        {
            return null;
        }

       //缓存存在
        RedisData redisData = JSONUtil.toBean(s,RedisData.class);
        //因为redisdata里面的data类型是object
        //所以data类型是JSONObject，不能直接强转为shop
        JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);

        System.out.println("缓存存在");

        //缓存没有过期=》直接返回
        if( redisData.getExpireTime().isAfter( LocalDateTime.now() ) )
        {
            System.out.println("缓存未过期");
            return shop;
        }
        //缓存过期，缓存重建

        System.out.println("缓存过期");

            //获取互斥锁
        boolean isLock = tryLock(LOCK_SHOP_KEY + id);
        //成功，新开一个线程去查询数据库
        if(isLock)
        {
            CACHE_REBUILD_EXECUTOR.submit(()->{

                //重建缓存
               try {
                   this.saveShop2Redis(id,CACHE_SHOP_TTL);
                   System.out.println("缓存重建成功");
               }catch (Exception e){
                    throw  new RuntimeException();
               }finally {
                   //最终都要释放锁
                   release(LOCK_SHOP_KEY + id);
               }


            });
        }
        //不管成功还是失败，都直接返回shop
        return shop;
    }


    /**
     * 用互斥锁解决缓存击穿问题
     *
     * @param id
     * @return
     */
    @Override
    public Shop  queryWithMutex(Long id) {

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
        if(!StrUtil.isNotBlank(s))     //也会排除""的情况
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


}
