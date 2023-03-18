package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private RedisIDWorker redisIDWorker;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Override
    public Result seckillVoucher(Long voucherId) {

        //查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        //秒杀还未开始，返回错误
        if( voucher.getBeginTime().isAfter(LocalDateTime.now()) )
        {
            return Result.fail("活动还未开始");
        }

        //秒杀已经结束，返回错误
        if(voucher.getEndTime().isBefore(LocalDateTime.now()))
        {
            return Result.fail("活动已经结束");
        }

        //优惠券数量不足，返回错误
        Integer StockNum = voucher.getStock();
        if(StockNum <1)
        {
            return Result.fail("库存数量不足");
        }



        Long Usrid = UserHolder.getUser().getId();

        //根据用户的id进行加锁
        synchronized (Usrid.toString().intern())     //intern方法解决tostring底层的缺陷
        {
            //return createOrder(voucherId);      //这种方法会导致事务失效

            //获取代理对象
            IVoucherOrderService orderService =(IVoucherOrderService) AopContext.currentProxy();
            return orderService.createOrder(voucherId);
        }

    }

    /**
     * 查询用户是否已经下过单，如果没有的话，就创建订单
     * 涉及查询和插入操作，需要开启事务
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createOrder(Long voucherId)
    {
        //用户id可以通过线程变量获取
        Long Usrid = UserHolder.getUser().getId();

        //解决一人一单问题：先查询用户是否已经下过单，如果下过单，直接返回错误信息
        LambdaQueryWrapper<VoucherOrder> voucherOrderLambdaQueryWrapper = new LambdaQueryWrapper<>();
        voucherOrderLambdaQueryWrapper.eq(VoucherOrder::getUserId,Usrid);
        int count = this.count(voucherOrderLambdaQueryWrapper);
        //如果用户已经下过单了，那么直接返回错误
        if(count>0)
        {
            return Result.fail("该用户已下过单！");
        }

        //库存减一
        //乐观锁解决超卖问题
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                .update();

        if(!success)
        {
            return Result.fail("库存不足！");
        }

        //创建订单（设置用户id、优惠券id，订单id）
        VoucherOrder order = new VoucherOrder();
        order.setVoucherId(voucherId);
        order.setUserId(Usrid);
        //为订单生成全局唯一ID
        long orderId = redisIDWorker.nextId("shop");
        order.setId(orderId);

        System.out.println("新的订单id为  "+orderId);

        //新增订单
        this.save(order);
        return Result.ok(orderId);
    }

}
