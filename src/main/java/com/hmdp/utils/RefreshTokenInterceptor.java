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

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @Author: dy
 * @Date: 2023/10/15 21:55
 * @Description:
 */
@Slf4j
public class RefreshTokenInterceptor implements HandlerInterceptor {

    //  这里的 Redis 要通过 构造方法来获取, 因为这个类并没有被 Spring 管理
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //  这里不做登陆校验功能, 此拦截器拦截所有请求

        //  1. 获取请求头中的 token
        String token = request.getHeader("authorization");

        log.info("获取到的 token: {}", token);

        //  判断是否没有获取到 token

        if (StrUtil.isBlank(token)) {
            //  未得到 token, 直接放行
            return true;
        }

        String key = LOGIN_USER_KEY + token;

        //  2. 根据 token 获取 User 对象
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);

        //  3. 判断用户是否为 null
        if (userMap.isEmpty()) {
            //  4. 用户不存在

            return true;
        }

        //  5. 将获取到的 HashMap 转换为 User 对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //  6. 将 User 对象添加到 ThreadLocal 中
        UserHolder.saveUser(userDTO);


        //  7. 刷新 token 有限期
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //  8. 放行

        return true;
    }

    // TODO: 2023/10/16 这个方法的作用!!!
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
