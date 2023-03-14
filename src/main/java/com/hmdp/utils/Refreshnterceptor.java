package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Refreshnterceptor implements HandlerInterceptor {

    private StringRedisTemplate redisTemplate;

    public Refreshnterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    //因为不是spring管理的对象，不能用autowire，只能外界通过setter注入


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        System.out.println("请求拦截..............");
        System.out.println("请求路径  "+request.getRequestURI());

        //获取用户的token（通过请求头里面的authorization字段）
        String authorization = request.getHeader("authorization");

        //如果token为空，直接打回
        if(StrUtil.isBlank(authorization))
        {
            System.out.println("token为空===>  " + authorization);
//            response.setStatus(401);
            //这里直接放行
            return true;
        }

        //否则带着token在redis里面进行查询
        String user_token = RedisConstants.LOGIN_USER_KEY + authorization;

        //如果查询出来用户为空，直接打回（token过期了）
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(user_token);
        if(entries.isEmpty())
        {
            System.out.println("用户数据不存在");
//            response.setStatus(401);
            //这里直接放行
            return true;
        }
        //否则就刷新用户的token有效期
        redisTemplate.expire(user_token,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //将查询得到的map对象转化为userdto对象
        UserDTO dto = BeanUtil.fillBeanWithMap(entries, new UserDTO(), false);
        // 用户的信息保存在线程变量里面
        UserHolder.saveUser(dto);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
