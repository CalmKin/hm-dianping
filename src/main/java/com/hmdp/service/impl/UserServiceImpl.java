package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {


    @Override
    public Result sendMsg(String phone, HttpSession session)
    {
        //验证手机号是否正确
        if(RegexUtils.isPhoneInvalid(phone))
        {
            //如果手机号不正确，直接返回错误信息
            return Result.fail("手机格式有误！");
        }

        //生成code
        String code = RandomUtil.randomNumbers(6);

        //先保存在session
        session.setAttribute("code",code);

        //再发送验证码(用日志模拟)
        log.debug("验证码是=====>>>>{}",code);

        return Result.ok();
    }
}
