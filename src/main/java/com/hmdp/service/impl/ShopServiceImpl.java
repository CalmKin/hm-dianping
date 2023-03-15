package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONString;
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
     * 缓存店铺数据
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {

        //先查询redis
        String s = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        Shop shop = null;

        //如果redis里面存在店铺数据，将字符串转换成对象返回
        if(!StrUtil.isBlank(s))     //也会排除""的情况
        {
            shop = JSON.parseObject(s, Shop.class);
            return Result.ok(shop);
        }

        if( s != null ) //如果没有进去上面那种情况，那么就是 ""了
        {
            return Result.fail("店铺不存在");
        }

        //否则查询mysql数据库
        shop  = this.getById(id);

        //如果数据不存在，缓存空值，然后再返回
        if(shop == null)
        {
            //空值的有效期不要设置那么长
            redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在");
        }

        //否则先将数据存进redis中
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSON.toJSONString(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //然后将数据返回用户
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
