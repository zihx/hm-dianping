package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.ShopService;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
* @author lenovo
* @description 针对表【tb_shop】的数据库操作Service实现
* @createDate 2024-04-16 14:03:32
*/
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop>
    implements ShopService{

    @Autowired
    private ShopMapper shopMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryById(Long id) {
        Shop shop = queryByIdWithPassThrough(id);
//        Shop shop = queryByIdWithMutex(id);
//        Shop shop = queryByIdWithLogicalExpire(id);
        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }

    // 基于逻辑过期解决缓存击穿
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);


    public Shop queryByIdWithLogicalExpire(Long id) {
        // 1.从redis查询商铺缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = redisTemplate.opsForValue().get(key);

        // 2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }

        // 4.命中，需要将json反序列化为对象
        // redisData没有数据
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1未过期，直接返回店铺信息
            return shop;
        }

        // 5.2已过期，需要缓存重建
        // 6.缓存重建
        // 6.1.获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean islock = tryLock(lockKey);
        // 6.2.判断是否获取互斥锁成功
        if (islock) {
            // 6.3.成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit( () -> {
                try {
                    // 重建缓存，过期时间为20L
                    saveShopRedis(id,30L);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                } finally {
                    releaseLock(lockKey);
                }
            });
        }
        // 6.4.返回过期店铺信息
        return shop;
    }

    public void saveShopRedis(Long id, Long expireMinutes) {
        // 1.查询店铺数据
        Shop shop = shopMapper.selectById(id);
        // 2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(expireMinutes));  // 过期时间
        // 3.写入redis
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    // 解决缓存穿透，同时基于互斥锁解决缓存击穿
    private Shop queryByIdWithMutex(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = redisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null) {
            return null;
        }
        String lockKey = null;
        Shop shop = null;
        try {
            lockKey = RedisConstants.LOCK_SHOP_KEY + id;
            boolean flag = tryLock(lockKey);
            if (!flag) {
                Thread.sleep(50);
                return queryByIdWithMutex(id);
            }
            shop = shopMapper.selectById(id);
            if (shop == null) {
                redisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            releaseLock(lockKey);
        }
        return shop;
    }

    // 解决缓存穿透
    private Shop queryByIdWithPassThrough(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        String shopJson = redisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        if (shopJson != null) {
            return null;
        }
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            redisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺id不能为空");
        }
        shopMapper.updateById(shop);
        redisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    private boolean tryLock(String key) {
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void releaseLock(String key) {
        redisTemplate.delete(key);
    }
}




