package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.service.ShopTypeService;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
* @author lenovo
* @description 针对表【tb_shop_type】的数据库操作Service实现
* @createDate 2024-04-16 14:03:32
*/
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType>
    implements ShopTypeService{

    @Autowired
    private ShopTypeMapper shopTypeMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public Result queryTypeList() {
        String shopTypeCache = redisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE_KEY);
        if (StrUtil.isNotBlank(shopTypeCache)) {
            List<ShopType> list = JSONUtil.toList(shopTypeCache, ShopType.class);
            return Result.ok(list);
        }
        List<ShopType> shopTypes = shopTypeMapper.selectList(new LambdaQueryWrapper<ShopType>().orderByAsc(ShopType::getSort));
        if (shopTypes == null) {
            return Result.fail("查询失败");
        }
        redisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopTypes), RedisConstants.CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(shopTypes);
    }
}




