package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.service.ShopService;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author lenovo
 * @description 针对表【tb_shop】的数据库操作Service实现
 * @createDate 2024-04-16 14:03:32
 */
@Service("shopService")
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {

    @Resource
    private ShopMapper shopMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("商铺不存在");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺id不能为空");
        }
        shopMapper.updateById(shop);
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            Page<Shop> page = shopMapper.selectPage(
                    new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE),
                    new LambdaQueryWrapper<Shop>().eq(Shop::getTypeId, typeId));
            return Result.ok(page.getRecords());
        }

        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        // 获取count条数据
        int count = current * SystemConstants.DEFAULT_PAGE_SIZE;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        // 以一个坐标为圆心
                        GeoReference.fromCoordinate(x, y),
                        // 默认单位为 m
                        new Distance(5000),
                        // 无法指定从哪开始到哪结束，只能指定数量，即返回0 ~ count - 1
                        // 第一页即0 ~ count - 1，后续页需要跳过(current - 1) * SystemConstants.DEFAULT_PAGE_SIZE个数据
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(count)
                );
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        // 跳过前count条数据
        count = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        // 没有下一页
        if (list.size() <= count) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> shopIdList = new ArrayList<>(list.size());
        Map<String, Distance> map = new HashMap<>();
        list.stream().skip(count).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            shopIdList.add(Long.parseLong(shopIdStr));
            map.put(shopIdStr, result.getDistance());
        });
        List<Shop> shopList = shopMapper.queryShopByIds(shopIdList);
        if (shopList == null || shopList.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        shopList.forEach(shop -> shop.setDistance(map.get(String.valueOf(shop.getId())).getValue()));
        return Result.ok(shopList);
    }
}




