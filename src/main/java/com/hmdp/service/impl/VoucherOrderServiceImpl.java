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

        //库存减一
        voucher.setStock(StockNum-1);
        LambdaQueryWrapper<SeckillVoucher> lqw = new LambdaQueryWrapper<>();
        lqw.eq(SeckillVoucher::getVoucherId,voucherId);

        //乐观锁解决超卖问题
        lqw.gt(SeckillVoucher::getStock,0);

        seckillVoucherService.update(voucher,lqw);

        //创建订单（设置用户id、优惠券id，订单id）
        VoucherOrder order = new VoucherOrder();
        order.setVoucherId(voucherId);

        Long Usrid = UserHolder.getUser().getId();
        order.setUserId(Usrid);

        long orderId = redisIDWorker.nextId("shop");
        order.setId(orderId);

        System.out.println("新的订单id为  "+orderId);

        //新增订单
        this.save(order);
        return Result.ok(orderId);
    }
}
