package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author lenovo
* @description 针对表【tb_voucher】的数据库操作Service
* @createDate 2024-04-16 14:03:32
*/
public interface VoucherService extends IService<Voucher> {

    void addSeckillVoucher(Voucher voucher);

    Result queryVoucherOfShop(Long shopId);
}
