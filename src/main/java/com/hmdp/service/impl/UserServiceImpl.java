package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
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
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    /**
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */
    public Result sendCode(String phone, HttpSession session) {

        //  1. 校验手机号是否合法
        //      这个玩意, 手机号码符合规定返回 false
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号输入错误");
        }
        //  2. 生成验证码
        String cacheCode = RandomUtil.randomNumbers(6);

        //  3. 保存验证码到 session
        session.setAttribute("code", cacheCode);

        //  4. 发送验证码
        log.info("发送验证码: {}", cacheCode);
        //  5. 返回结果


        return Result.ok();
    }

    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();

        //  1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号不符合规定");
        }

        //  2. 校验验证码是否一致
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();

        if (!cacheCode.toString().equals(code)) {
            return Result.fail("验证码错误");
        }

        //  3. 根据手机号查询用户是否存在

        User user = query().eq("phone", phone).one();

        if (user == null) {
            log.info("用户不存在, 创建用户");
            //  4. 用户不存在, 创建用户
            user = createUserWithPhone(phone);
        }
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);


        //  5. 保存用户到 session
        session.setAttribute("user", userDTO);

        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        //  新建用户
        User user = User.builder()
                .phone(phone)
                .nickName(USER_NICK_NAME_PREFIX + RandomUtil
                                                    .randomString(10)).build();

        //  保存用户
        save(user);
        return user;
    }
}
