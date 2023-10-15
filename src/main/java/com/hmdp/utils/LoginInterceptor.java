package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * @Author: dy
 * @Date: 2023/10/15 21:55
 * @Description:
 */
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //  1. 获取 session
        HttpSession session = request.getSession();

        //  2. 获取 session 中的用户
        Object user = session.getAttribute("user");

        //  3. 用户不存在
        if (user == null) {
            response.setStatus(401);
            return false;
        }

        //  4. 用户存在, 报错用户信息到 ThreadLocal
        UserHolder.saveUser((UserDTO) user);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
