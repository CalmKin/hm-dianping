package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.判断是否需要根据坐标查询
        if(x==null || y==null)
        {
            LambdaQueryWrapper<Shop> lqw = new LambdaQueryWrapper<>();
            lqw.eq(Shop::getTypeId,typeId);
            IPage<Shop> page = new Page<>();
            IPage<Shop> ret = this.page(page, lqw);
            return Result.ok(ret.getRecords());
        }
        //2.计算分页参数
        int from = (current-1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        //3.查询redis，按照距离排序、分页。结果：shopId，distance,因为redis的geo操作没有from。。to，指定的参数只能是从头开始多少条，所以要手动截取

        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> search = redisTemplate.opsForGeo().search(key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.
                        newGeoSearchArgs().includeDistance().limit(end));
        //判空
        if(search==null || search.getContent().isEmpty())
        {
            return Result.ok(Collections.EMPTY_LIST);
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = search.getContent();

        //3.5逻辑分页，手动截取后半部分
            //如果from已经大于查到的所有数据了，直接返回空
        if(from >= content.size())
        {
            return Result.ok(Collections.EMPTY_LIST);
        }

            //手动跳过from前面的数据
            List<Long> ids = new ArrayList<>(content.size());
        //保存每个店铺对应的距离
        Map<String,Distance> dis = new HashMap<>(content.size());
        content.stream().skip(from).forEach(
                    item->{
                        //4.解析出id
                        String shopIdStr = item.getContent().getName();
                        ids.add(Long.valueOf(shopIdStr));
                        // 4.3.获取距离
                        Distance averageDistance = search.getAverageDistance();
                        dis.put(shopIdStr,averageDistance);
                    }
                    );
        //5.根据id查询shop
        String strs = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + strs + ")").list();

        for (Shop shop : shops) {
            //给每个店铺赋上距离
            shop.setDistance( dis.get(shop.getId().toString()).getValue() );
        }

        //6.返回
        return Result.ok(shops);
    }




}
