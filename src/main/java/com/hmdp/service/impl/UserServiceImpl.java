package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
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

    @Autowired
    private StringRedisTemplate redisTemplate;


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

        //先保存在redis
        redisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone , code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

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

        //获取存在redis里面的验证码
        String cache_code = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);

        if( cache_code==null || !user_code.equals( cache_code ) )
        {
            return Result.fail("验证码错误!");
        }

        LambdaQueryWrapper<User> lqw = new LambdaQueryWrapper<>();
        lqw.eq(User::getPhone,phone);
        User user = this.getOne(lqw);
        //用户不存在数据库，就顺便创建用户实现注册
        if(user == null)
        {
            user = createUser(phone);
        }

        UserDTO dto = new UserDTO();
        BeanUtils.copyProperties(user,dto);

        //随机生成token作为用户的登录凭证
        String token = UUID.randomUUID().toString();


        // todo: 存在类型转换的问题,long类型不能隐式转化为string类型
        //将用户信息转化成map变量，方便值以hash的形式存进redis中
        //这里为了方便，就通过工具类将所有属性值都转化成string
        Map<String, Object> usrMap = BeanUtil.beanToMap(dto,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue)->fieldValue.toString())//这里解决long类型不能自动转化成string类型的问题
                );

        //将用户信息存进redis，还需要设置一个有效期
        String token_key = LOGIN_USER_KEY + token;

        redisTemplate.opsForHash().putAll(token_key,usrMap);
        redisTemplate.expire(token_key,LOGIN_USER_TTL,TimeUnit.MINUTES);

        //将token返回给前端，方便下次发送请求的时候携带token
        return Result.ok(token);
    }

    /**
     * 用户签到功能
     * @return
     */
    @Override
    public Result sign() {

        Long usrId = UserHolder.getUser().getId();

        String keySuffix = LocalDateTime.now().format( DateTimeFormatter.ofPattern(":yyyy:MM"));

        String key = USER_SIGN_KEY + usrId +keySuffix;

        int dayOfMonth = LocalDateTime.now().getDayOfMonth();

        redisTemplate.opsForValue().setBit(key,dayOfMonth-1,true);

        return Result.ok();
    }

    private User createUser(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        this.save(user);
        return user;
    }
}
