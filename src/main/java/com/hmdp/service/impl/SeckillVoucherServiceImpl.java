package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.service.SeckillVoucherService;
import com.hmdp.mapper.SeckillVoucherMapper;
import org.springframework.stereotype.Service;

/**
* @author lenovo
* @description 针对表【tb_seckill_voucher(秒杀优惠券表，与优惠券是一对一关系)】的数据库操作Service实现
* @createDate 2024-04-16 14:03:32
*/
@Service
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher>
    implements SeckillVoucherService{

}




