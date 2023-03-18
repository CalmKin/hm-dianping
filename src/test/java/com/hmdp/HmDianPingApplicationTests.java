package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl service;

    @Test
    public void cache_build()
    {

    }


    @Test
    public void query()
    {
        service.queryById(1L);

    }


}
