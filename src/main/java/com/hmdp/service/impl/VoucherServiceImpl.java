package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.service.VoucherService;
import com.hmdp.mapper.VoucherMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
* @author lenovo
* @description 针对表【tb_voucher】的数据库操作Service实现
* @createDate 2024-04-16 14:03:32
*/
@Service("voucherService")
@Slf4j
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher>
    implements VoucherService{

    @Resource
    private VoucherMapper voucherMapper;

    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    @Override
    @Transactional
    public void addSeckillVoucher(Voucher voucher) {
        voucherMapper.insert(voucher);
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherMapper.insert(seckillVoucher);
    }

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        List<Voucher> vouchers = voucherMapper.queryVoucherOfShop(shopId);
        return Result.ok(vouchers);
    }
}




