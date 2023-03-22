package com.hmdp.config;

import io.lettuce.core.RedisClient;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    /**
     * 将redisson的配置类作为bean对象暴露出去
     * @return
     */
    @Bean
    public RedissonClient redissonClient()
    {
        Config config = new Config();
        //使用单节点模式，设置url，设置密码
        config.useSingleServer().setAddress("redis://127.0.0.1:6379")
                .setPassword("12345678");
        return Redisson.create(config);
    }

    @Bean
    public RedissonClient redissonClient2()
    {
        Config config = new Config();
        //使用单节点模式，设置url，设置密码
        config.useSingleServer().setAddress("redis://127.0.0.1:6379")
                .setPassword("12345678");
        return Redisson.create(config);
    }
    @Bean
    public RedissonClient redissonClient3()
    {
        Config config = new Config();
        //使用单节点模式，设置url，设置密码
        config.useSingleServer().setAddress("redis://127.0.0.1:6379")
                .setPassword("12345678");
        return Redisson.create(config);
    }

}
