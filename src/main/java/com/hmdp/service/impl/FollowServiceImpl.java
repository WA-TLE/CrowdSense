package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.FOLLOW_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author dy
 * @since 2023/10/16 10:06
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    /**
     * 关注 or 取关博主
     *
     * @param id
     * @param flag
     * @return
     */
    public Result follow(Long id, Boolean flag) {
        //  1. 获取当前用户 id
        Long userId = UserHolder.getUser().getId();

        String key = FOLLOW_KEY + userId;

        if (flag) {
            // 2. 关注当前博主
            Follow follow = new Follow();
            follow.setFollowUserId(id);
            follow.setUserId(userId);

            //  2.2 保存到数据库
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, id.toString());
            }
        } else {
            //  3. 取消关注
            //  3.2 从数据库取消关注
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", id));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, userId.toString());
            }
        }


        return Result.ok();
    }

    /**
     * 根据博主 id, 查询目前登录用户是否关注博主
     *
     * @param id
     * @return
     */
    public Result isFollow(Long id) {
        //  1. 获取当前登录用户
        Long userId = UserHolder.getUser().getId();

        //  2. 查询数据库中是否有当前数据
        Integer count = query().eq("user_id", userId)
                .eq("follow_user_id", id).count();
        return Result.ok(count > 0);
    }

    /**
     * 查询共同关注
     *
     * @param id
     * @return
     */
    public Result followCommon(Long id) {
        //  1. 获取当前用户 id
        Long userId = UserHolder.getUser().getId();

        //  2. 获取两个当前用户的 key 和博主用户的 key
        String key1 = FOLLOW_KEY + userId;
        String key2 = FOLLOW_KEY + id;

        //  3. 求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);

        //  4. 排除共同好友为 null 的情况
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok();
        }

        //  5. 解析 集合
        // 3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        // 4.查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);

    }
}
