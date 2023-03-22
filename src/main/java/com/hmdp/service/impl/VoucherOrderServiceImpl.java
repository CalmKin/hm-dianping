package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.*;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.StringValue;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Transactional
@Slf4j
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private RedisIDWorker redisIDWorker;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Resource
    private RedissonClient redissonClient;


    public static final DefaultRedisScript<Long> SECKILL_SCRIPT;     //初始化lua脚本
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));     //配置脚本路径
        SECKILL_SCRIPT.setResultType(Long.class);    //设置返回值类型
    }

    public static final ExecutorService SECKILL_EXECUTOR = Executors.newSingleThreadExecutor();
    String queueName = "stream.orders";

    //让这个类刚创建的时候就执行这个线程的任务
    @PostConstruct
    private void init()
    {

        SECKILL_EXECUTOR.submit(new VoucherOrderHandler());
    }

        private class VoucherOrderHandler implements  Runnable{     //重写线程任务
            @Override
            public void run() {
                while(true)
                {
                    try {

                        log.info("开始获取消息队列。。。");
                        Thread.sleep(2000);
                        //1.获取消息队列里面的订单消息
                        List<MapRecord<String, Object, Object>> msg = redisTemplate.opsForStream().read(
                                Consumer.from("g1", "c1"),
                                StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                                StreamOffset.create(queueName, ReadOffset.lastConsumed())     //每次读取最新的消息
                        );
                        //2.判断消息获取是否成功
                        if(msg == null || msg.isEmpty()) {
                            //如果获取失败，说明没有消息，继续下一次循环
                            log.info("消息队列为空");
                            continue;
                        }
                        //3.获取消息成功，解析消息里面的订单信息
                        MapRecord<String, Object, Object> entries = msg.get(0);     //第一个string就是这个消息对应的id号
                        Map<Object, Object> value = entries.getValue();
                        VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

                        log.info("读取消息队列成功");

                        //4.获取成功，进行下单
                        handleVoucherOrder(voucherOrder);

                        //5.进行ACK确认
                        redisTemplate.opsForStream().acknowledge(queueName,"g1",entries.getId());

                    } catch (Exception e) {
                        //如果出现异常，消息进入pendinglist里面，单独进行处理
                        handlePendingList();
                    }

                }
            }
        }
    //线程任务用内部类实现runnable接口来实现

    /**
     * 发生异常之后，单独处理pendingList里面的消息
     */
    private void handlePendingList() {
        while(true)
        {
            try {
                //1.获取消息队列里面的订单消息
                List<MapRecord<String, Object, Object>> msg = redisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(queueName, ReadOffset.from("0"))     //每次读取最新的消息
                );
                //2.判断消息获取是否成功
                if(msg == null || msg.isEmpty()) {
                    //如果获取失败，说明没有消息，可以直接退出循环
                    break;
                }
                //3.获取消息成功，解析消息里面的订单信息
                MapRecord<String, Object, Object> entries = msg.get(0);     //第一个string就是这个消息对应的id号
                Map<Object, Object> value = entries.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);

                //4.获取成功，进行下单
                handleVoucherOrder(voucherOrder);

                //5.进行ACK确认
                redisTemplate.opsForStream().acknowledge(queueName,"g1",entries.getId());
                Thread.sleep(2000);
            } catch (Exception e) {
                //如果出现异常，则进入下一次循环，进行读取消息,保证异常的订单一定可以读取成功
                log.info("pending-list读取异常");

            }

        }
    }

    public    IVoucherOrderService proxy ;

    /**
     * 线程任务执行的函数(用分布式锁解决用户在多个节点上进行下单的操作)
     * 此时因为是线程池里面执行的任务，所以不能再用threadlocal来获取用户id了
     * 因为代理对象也是基于threadlocal，所以线程任务里面也不能获取代理对象了
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long Usrid =voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("lock:shop:" + Usrid);
        boolean flag = lock.tryLock();
        if(!flag)   //这里基本上不会发生，只是作为兜底方案
        {
            log.info("一个用户只能下一单");
        }

        try {
            //调用代理对象的数据库操作
            System.out.println(proxy);
            proxy.createOrder(voucherOrder);
        }finally {
            //释放锁
            lock.unlock();
        }
    }

    /**
     * 创建订单
     * 涉及查询和插入操作，需要开启事务
     *
     * @param
     */
    @Transactional
    public void createOrder(VoucherOrder order)
    {
        //用户id不能通过线程变量获取了
        Long Usrid = order.getUserId();

        //解决一人一单问题：先查询用户是否已经下过单，如果下过单，直接返回错误信息
        LambdaQueryWrapper<VoucherOrder> voucherOrderLambdaQueryWrapper = new LambdaQueryWrapper<>();
        voucherOrderLambdaQueryWrapper.eq(VoucherOrder::getUserId,Usrid);
        int count = this.count(voucherOrderLambdaQueryWrapper);
        //如果用户已经下过单了，那么直接返回错误（兜底方案，基本不会发生）
        if(count>0)
        {
            log.info("该用户已下过单！");
            return;
        }

        //库存减一
        //乐观锁解决超卖问题
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", order.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
                .update();

        if(!success)   //兜底方案
        {
            log.info("库存不足");
            return ;
        }

        //新增订单
        this.save(order);
    }

    /***
     *  秒杀券下单
     * @param voucherId
     * @return 订单id
     */
    @Override
    public Result seckillVoucher(Long voucherId) {

        Long usrId = UserHolder.getUser().getId();

        //为订单生成全局唯一ID
        long orderID = redisIDWorker.nextId("order");
        //执行lua脚本，进行订单信息修改
        Long res = redisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), usrId.toString(), String.valueOf(orderID));
        //将lua脚本返回值转化为int类型
        int ret = res.intValue();
        //如果订单获取异常
        if(ret!=0)
        {
            return Result.fail( ret==1 ? "库存不足" : "不能重复下单"  );
        }

        //因为线程池里面没法获取当前对象的代理对象，所以这里提前设置好
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        //到了这里，用户已经可以获得订单号了
        return Result.ok(orderID);
    }
}
