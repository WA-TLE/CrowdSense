package com.hmdp.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author dy
 * @since 2023/10/16 10:06
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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

        //  3. 保存验证码到 Redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, cacheCode, LOGIN_CODE_TTL, TimeUnit.MINUTES);

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
        //  2. 1 从 session 中取出 验证码
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();

        if (cacheCode == null || !cacheCode.equals(code)) {
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


        //  5. 保存用户到 Redis
        //  5.1 将用户转化为 HashMap
//        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
//                );

        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),

                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        //  5.2 随机生成 token 用作 key
        String token = UUID.randomUUID().toString(true);


        //  5.3 保存用户

        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        //  设置用户储存时间
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //  新建用户
        User user = User.builder()
                .phone(phone)
                .nickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10))
                .icon("")   //  防止用户转换为 Map 的时候出现空指针异常
                .build();

        //  保存用户
        save(user);
        return user;
    }

    @Override
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.delete(key);
        return Result.ok("您已成功退出登录~");
    }

    /**
     * 用户签到功能
     *
     * @return
     */
    public Result signIn() {
        //  1. 获取当前用户 id
        Long userid = UserHolder.getUser().getId();

        log.info("用户: {}开始签到", userid);

        //  2. 获取当前日期
        LocalDateTime now = LocalDateTime.now();

        //  3. 拼接 key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userid.toString() + keySuffix;

        //  4. 获取今天是几号
        int dayOfMonth = now.getDayOfMonth();

        //  5. 存入 Redis
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    /**
     * 统计用户连续签到天数
     *
     * @return
     */
    public Result signInCount() {
        //  1. 获取当前用户 id
        Long userid = UserHolder.getUser().getId();

        log.info("统计用户: {} 连续签到天数", userid);

        //  2. 获取当前日期
        LocalDateTime now = LocalDateTime.now();

        //  3. 拼接 key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userid.toString() + keySuffix;

        //  4. 获取今天是几号
        int dayOfMonth = now.getDayOfMonth();

        //  5. 获取截止到今天本月所有的签到记录
        List<Long> result = stringRedisTemplate.opsForValue()
                .bitField(
                        key,
                        BitFieldSubCommands.create()
                                .get(BitFieldSubCommands.BitFieldType
                                        .unsigned(dayOfMonth))
                                .valueAt(0)
                );

        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }

        //  获取截止到今天本月所有的签到记录 --> 十进制数
        Long num = result.get(0);
        log.info("连续签到所获得的十进制数: {}", num);

        if (num == null || num == 0) {
            return Result.ok(0);
        }

        //  遍历查询连续签到天数
        int count = 0;
        while (num > 0) {
            if ((num & 1) == 1) {
                count++;
            } else {
                break;
            }

            num = (num >> 1);
        }

        log.info("连续签到天数: {}", count);
        return Result.ok(count);
    }
}
