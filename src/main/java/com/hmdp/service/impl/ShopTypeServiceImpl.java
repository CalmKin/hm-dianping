package com.hmdp.service.impl;

import com.alibaba.fastjson.JSON;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public List<ShopType> queryList() {

        String shop_types = redisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);

        List<ShopType> ret = null;

        if(shop_types!=null)
        {
            ret = JSON.parseArray(shop_types, ShopType.class);
            return ret;
        }

        //如果缓存中不存在，查询数据库
        ret = this.query().orderByAsc("sort").list();
        //将数据存到redis中
        redisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY,JSON.toJSONString(ret));

        return ret;
    }
}
