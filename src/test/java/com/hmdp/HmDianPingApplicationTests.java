package com.hmdp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.RedisCommand;
import org.springframework.data.redis.core.StringRedisTemplate;
import sun.security.action.GetLongAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    public void InsertGeo(){

        //获取所有店铺
        List<Shop> list = shopService.list();

        //根据shop的getTypeId对店铺进行分类
        Map<Long, List<Shop>> collect = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));


        for( Map.Entry<Long,List<Shop>> entry : collect.entrySet() )
        {
            Long key = entry.getKey();  //店铺类型id
            List<Shop> value = entry.getValue(); //该类型包含的店铺

            String k = SHOP_GEO_KEY + key;

            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());

            for (Shop shop : value) {
                locations.add(
                        new RedisGeoCommands.GeoLocation<String>(shop.getId().toString(),
                                new Point(shop.getX(),shop.getY())
                        ));
            }

            //一次性批量导入
            redisTemplate.opsForGeo().add(k,locations);

        }


    }
}
