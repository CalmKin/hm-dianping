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
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
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

    @Resource
    private CacheClient client;

    /**
     * 解决缓存击穿问题
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = client.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById , CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //用互斥锁解决缓存击穿问题
        Shop shop=client.queryWithMutex(CACHE_SHOP_KEY, LOCK_SHOP_KEY,id,Shop.class,this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //用逻辑过期时间解决缓存击穿问题
        //Shop shop=client.queryWithLogicalExpire(CACHE_SHOP_KEY, LOCK_SHOP_KEY,id,Shop.class,this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if(shop == null)
        {
            return Result.fail("商品信息不存在");
        }
       // System.out.println("商品信息："+shop.toString());
        return Result.ok(shop);
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
