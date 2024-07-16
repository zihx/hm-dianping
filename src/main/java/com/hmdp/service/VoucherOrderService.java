package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author lenovo
* @description 针对表【tb_voucher_order】的数据库操作Service
* @createDate 2024-04-16 14:03:32
*/
public interface VoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);
}
