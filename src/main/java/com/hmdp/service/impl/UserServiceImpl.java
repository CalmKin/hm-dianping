package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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

    /**
     * 验证码登录模块
     * 注意，因为发送验证码和登录是两个独立的请求，所以这里还需要再次校验手机号
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String user_code = loginForm.getCode();
        String phone = loginForm.getPhone();

        if(RegexUtils.isPhoneInvalid(phone))
        {
            return Result.fail("手机号有误");
        }

        //校验验证码    细节：有可能session里面根本不存在code，所以还需要判空
        Object cache_code = session.getAttribute("code");
        if( cache_code==null || !user_code.equals( cache_code.toString() ) )
        {
            return Result.fail("验证码错误!");
        }

        LambdaQueryWrapper<User> lqw = new LambdaQueryWrapper<>();
        lqw.eq(User::getPhone,phone);
        User user = this.getOne(lqw);

        if(user == null)
        {
            user = createUser(phone);
        }

        session.setAttribute("user",user);
        return Result.ok("登录成功");
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        this.save(user);
        return user;
    }
}
