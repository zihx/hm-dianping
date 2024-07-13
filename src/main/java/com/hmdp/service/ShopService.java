package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author lenovo
* @description 针对表【tb_shop】的数据库操作Service
* @createDate 2024-04-16 14:03:32
*/
public interface ShopService extends IService<Shop> {

    Result queryById(Long id);

    Result updateShop(Shop shop);
}
