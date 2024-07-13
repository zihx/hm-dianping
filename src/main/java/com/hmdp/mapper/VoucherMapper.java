package com.hmdp.mapper;

import com.hmdp.entity.Voucher;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author lenovo
* @description 针对表【tb_voucher】的数据库操作Mapper
* @createDate 2024-04-16 14:03:32
* @Entity com.hmdp.pojo.Voucher
*/
public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}




