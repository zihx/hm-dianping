package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ClassName: RedisConfig
 * Package: com.hmdp.config
 * Description:
 *
 * @Author Chao Fang
 * @Create 2024/7/15 21:10
 * @Version 1.0
 */
@Configuration
public class RedisConfig {
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.88.130:6379").setPassword("123456");
        return Redisson.create(config);
    }
}
