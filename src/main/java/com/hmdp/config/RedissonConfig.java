package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author: dy
 * @Date: 2023/10/27 20:41
 * @Description: Redisson客户端
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        //  配置
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://192.168.147.129:6379")
                .setPassword("dingyu");

        //  返回创建的对象
        return Redisson.create();
    }

}
