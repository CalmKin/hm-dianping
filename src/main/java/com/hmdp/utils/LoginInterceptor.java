package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.beans.BeanUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取session
        HttpSession session = request.getSession();
        //查看是否携带用户信息
        Object user = session.getAttribute("user");
        //如果属性为空，直接返回错误
        if(user == null)
        {
            response.setStatus(401);
            return false;
        }

        //否则通过拦截器，将用户变量提供给controller
        UserDTO dto = new UserDTO();
        BeanUtils.copyProperties((User)user,dto);
        UserHolder.saveUser(dto);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
