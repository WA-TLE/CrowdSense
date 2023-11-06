package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author dy
 * @since 2023/10/16 10:06
 */
public interface IUserService extends IService<User> {

    /**
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 登录功能
     *
     * @param loginForm
     * @param session
     * @return
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 登出功能
     *
     * @param request
     */
    Result logout(HttpServletRequest request);


    /**
     * 用户签到功能
     *
     * @return
     */
    Result signIn();


    /**
     * 统计用户连续签到天数
     *
     * @return
     */
    Result signInCount();
}
