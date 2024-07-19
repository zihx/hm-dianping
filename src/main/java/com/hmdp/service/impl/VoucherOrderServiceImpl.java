package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
* @author lenovo
* @description 针对表【tb_voucher_order】的数据库操作Service实现
* @createDate 2024-04-16 14:03:32
*/
@Service("voucherOrderService")
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
    implements VoucherOrderService{

    // 子线程无法直接获取代理对象，由父线程赋值子线程使用
    private VoucherOrderService voucherOrderService;

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

    // 获取lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // redis基于Stream的消息队列
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWorker.nextId("order");
        Long result = stringRedisTemplate.execute(
                // lua脚本
                SECKILL_SCRIPT,
                // key
                Collections.emptyList(),
                // arg
                voucherId.toString(), userId.toString(), orderId.toString());

        if (result.intValue() != 0) {
            return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
        }

        voucherOrderService = (VoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);
    }

    // 阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    // 性能优化，Java阻塞队列实现消息队列
    // @Override
    // public Result seckillVoucher(Long voucherId) {
    //     Long userId = UserHolder.getUser().getId();
    //     Long orderId = redisIdWorker.nextId("order");
    //     Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
    //     if (result.intValue() != 0) {
    //         return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
    //     }
    //
    //     VoucherOrder voucherOrder = new VoucherOrder();
    //     voucherOrder.setId(orderId);
    //     voucherOrder.setUserId(userId);
    //     voucherOrder.setVoucherId(voucherId);
    //     orderTasks.add(voucherOrder);
    //
    //     // 获取代理对象
    //     voucherOrderService = (VoucherOrderService) AopContext.currentProxy();
    //
    //     return Result.ok(orderId);
    // }

    // 线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 从redis基于Stream的消息队列中获取订单信息
    private class VoucherOrderHandler implements Runnable {
        private static final String QUEUE_NAME = "stream.orders";
        @Override
        public void run() {
            while (true) {
                try {
                    // 获取消息队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(QUEUE_NAME, ReadOffset.lastConsumed())
                    );
                    // 没有订单消息，继续下一次循环
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    // 创建订单
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                    // 发送ACK确实
                    stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME, "g1", record.getId());
                } catch (Exception e) {
                    log.error("创建订单异常", e);
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 获取消息队列中的订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(QUEUE_NAME, ReadOffset.from("0"))
                    );
                    // pending-list中没有消息，退出
                    if (list == null || list.isEmpty()) {
                        break;
                    }
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("创建pending-list订单异常", e);
                }
            }
        }
    }


    // 从Java阻塞队列中获取订单信息
    // private class VoucherOrderHandler implements Runnable {
    //     @Override
    //     public void run() {
    //         while (true) {
    //             try {
    //                 VoucherOrder voucherOrder = orderTasks.take();
    //                 handleVoucherOrder(voucherOrder);
    //             } catch (Exception e) {
    //                 log.error("创建订单异常", e);
    //             }
    //         }
    //     }
    // }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        // 不是同一线程，无法从UserHolder中获取用户id
        Long userId = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            log.error("不允许重复下单");
            return;
        }
        try {
            voucherOrderService.createVoucherOrder(voucherOrder);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock();
        }
    }

    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        Integer count = voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>().eq(VoucherOrder::getVoucherId, voucherId).eq(VoucherOrder::getUserId, userId));
        if (count > 0) {
            log.error("不能重复下单");
            return;
        }

        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId).gt("stock", 0).update();
        if (!success) {
            log.error("库存不足");
            return;
        }

        voucherOrderMapper.insert(voucherOrder);
    }

    // 性能较低
    // @Override
    // public Result seckillVoucher(Long voucherId) {
    //     SeckillVoucher seckillVoucher = seckillVoucherMapper.selectById(voucherId);
    //     if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
    //         return Result.fail("秒杀未开始");
    //     }
    //     if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
    //         return Result.fail("秒杀已结束");
    //     }
    //     if (seckillVoucher.getStock() <= 0) {
    //         return Result.fail("库存不足");
    //     }
    //
    //     // 查询该用户是否已经购买过，存在线程安全问题，不能保证一人一单
    //     // 因为在某线程订单创建完成之前，可能会有其它线程去查询，此时仍然查询不到订单，导致这些线程也会去创建订单
    //     // 由于是创建新的，而不是修改现有数据，无法使用悲观锁。
    //     // 在创建订单时获取锁，保证只有一个线程能创建订单
    //     // Integer count = voucherOrderMapper.selectCount(new LambdaQueryWrapper<VoucherOrder>().eq(VoucherOrder::getVoucherId, voucherId).eq(VoucherOrder::getUserId, userId));
    //     // if (count > 0) {
    //     //     return Result.fail("您已购买过此代金券");
    //     // }

    //     // 满足条件，创建订单
    //     Long userId = UserHolder.getUser().getId();
    //     // synchronized (userId.toString().intern()) {
    //     //     // 方法调用是通过代理进行才能触发@Transactional
    //     //     // 如果一个方法在其所在类内部调用另一个也带有@Transactional注解的方法，那么这个调用不会被视为一个代理调用。
    //     //     // return createVoucherOrder(voucherId);
    //     //
    //     //     // 获取代理对象
    //     //     VoucherOrderService voucherOrderService = (VoucherOrderService) AopContext.currentProxy();
    //     //     return voucherOrderService.createVoucherOrder(voucherId);
    //     // }
    //
    //     // synchronized在分布式场景并不适用
    //     // SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
    //     // boolean isLock = simpleRedisLock.tryLock(1200);
    //     // if (!isLock) {
    //     //     return Result.fail("不允许重复下单");
    //     // }
    //     // try {
    //     //     VoucherOrderService voucherOrderService = (VoucherOrderService) AopContext.currentProxy();
    //     //     return voucherOrderService.createVoucherOrder(voucherId);
    //     // } catch (Exception e) {
    //     //     throw new RuntimeException(e);
    //     // } finally {
    //     //     simpleRedisLock.unlock();
    //     // }
    //
    //     RLock lock = redissonClient.getLock("lock:order:" + userId);
    //     try {
    //         // 获取锁的最大等待时间（期间失败会重试），锁自动施放时间，时间单位
    //         // boolean isLock = lock.tryLock(1, 10, TimeUnit.SECONDS);
    //         // 失败直接返回,锁自动释放时间为30s
    //         boolean isLock = lock.tryLock();
    //         if (!isLock) {
    //             return Result.fail("不允许重复下单");
    //         }
    //         VoucherOrderService voucherOrderService = (VoucherOrderService) AopContext.currentProxy();
    //         return voucherOrderService.createVoucherOrder(voucherId);
    //     } catch (RuntimeException e) {
    //         throw new RuntimeException(e);
    //     } finally {
    //         lock.unlock();
    //     }
    // }

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