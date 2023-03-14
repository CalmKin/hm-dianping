package com.hmdp.service.impl;

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

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
        String s = redisTemplate.opsForValue().get(CACHE_SHOP_KEY + "id");

        Shop shop = null;

        //如果redis里面存在店铺数据，将字符串转换成对象返回
        if( s!= null )
        {
            shop = JSON.parseObject(s, Shop.class);
            return Result.ok(shop);
        }
        //否则查询mysql数据库
        shop  = this.getById(id);

        //如果数据不存在，直接返回
        if(shop == null)
        {
            return Result.fail("店铺不存在");
        }

        //否则先将数据存进redis中
        redisTemplate.opsForValue().set(CACHE_SHOP_KEY + "id",JSON.toJSONString(shop));
        //然后将数据返回用户
        return Result.ok(shop);
    }
}
