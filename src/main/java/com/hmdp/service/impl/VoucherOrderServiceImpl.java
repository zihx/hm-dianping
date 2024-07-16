package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.service.SeckillVoucherService;
import com.hmdp.service.VoucherOrderService;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.TimeUnit;

/**
* @author lenovo
* @description 针对表【tb_voucher_order】的数据库操作Service实现
* @createDate 2024-04-16 14:03:32
*/
@Service("voucherOrderService")
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
    implements VoucherOrderService{

    @Resource
    private SeckillVoucherService seckillVoucherService;
    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private VoucherOrderMapper voucherOrderMapper;
    @Resource
    private RedissonClient redissonClient;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectById(voucherId);
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        if (seckillVoucher.getStock() <= 0) {
            return Result.fail("库存不足");
        }

        // 查询该用户是否已经购买过，存在线程安全问题，不能保证一人一单
        // 因为在某线程订单创建完成之前，可能会有其它线程去查询，此时仍然查询不到订单，导致这些线程也会去创建订单
        // 由于是创建新的，而不是修改现有数据，无法使用悲观锁。
        // 在创建订单时获取锁，保证只有一个线程能创建订单
        // Integer count = voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>().eq(VoucherOrder::getVoucherId, voucherId).eq(VoucherOrder::getUserId, userId));
        // if (count > 0) {
        //     return Result.fail("您已购买过此代金券");
        // }
        Long userId = UserHolder.getUser().getId();
        // 满足条件，创建订单
        // synchronized (userId.toString().intern()) {
        //     // 方法调用是通过代理进行才能触发@Transactional
        //     // 如果一个方法在其所在类内部调用另一个也带有@Transactional注解的方法，那么这个调用不会被视为一个代理调用。
        //     // return createVoucherOrder(voucherId);
        //
        //     // 获取代理对象
        //     VoucherOrderService voucherOrderService = (VoucherOrderService) AopContext.currentProxy();
        //     return voucherOrderService.createVoucherOrder(voucherId);
        // }

        // synchronized在分布式场景并不适用
        // SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        // boolean isLock = simpleRedisLock.tryLock(1200);
        // if (!isLock) {
        //     return Result.fail("不允许重复下单");
        // }
        // try {
        //     VoucherOrderService voucherOrderService = (VoucherOrderService) AopContext.currentProxy();
        //     return voucherOrderService.createVoucherOrder(voucherId);
        // } catch (Exception e) {
        //     throw new RuntimeException(e);
        // } finally {
        //     simpleRedisLock.unlock();
        // }

        RLock lock = redissonClient.getLock("lock:order:" + userId);
        try {
            // 获取锁的最大等待时间（期间失败会重试），锁自动施放时间，时间单位
            // boolean isLock = lock.tryLock(1, 10, TimeUnit.SECONDS);
            // 失败直接返回,锁自动释放时间为30s
            boolean isLock = lock.tryLock();
            if (!isLock) {
                return Result.fail("不允许重复下单");
            }
            VoucherOrderService voucherOrderService = (VoucherOrderService) AopContext.currentProxy();
            return voucherOrderService.createVoucherOrder(voucherId);
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    // 当synchronized修饰一个方法时，它会确保一次只有一个线程可以执行该方法。
    // 即只有一个线程能创建订单，但我们的要求是不同用户的线程可以同时创建订单，同一用户只能有一个线程创建订单
    // 因此在方法内
    // synchronized (UserHolder.getUser().getId().toString().intern()) {
    //     // 临界区代码
    // }
    // 但由于存在事务，先释放锁，再提交事务（将数据保存在表中），在次期间仍然可能存在线程获取锁并且查询不到订单从而导致多次创建订单
    // 因此应该在事务提交后释放锁，即锁住整个函数调用

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>().eq(VoucherOrder::getVoucherId, voucherId).eq(VoucherOrder::getUserId, userId));
        if (count > 0) {
            return Result.fail("您已购买过此代金券");
        }

        // 更新库存
        // 存在线程安全问题
        // boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId).update();
        // 乐观锁
        // boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId).eq("stock", stock).update();
        // 无需一模一样，只要库存 > 0即可
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId).gt("stock", 0).update();
        if (!success) {
            return Result.fail("库存不足");
        }

        // 添加订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisIdWorker.nextId("order"));
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrderMapper.insert(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }
}




