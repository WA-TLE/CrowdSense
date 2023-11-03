package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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

        if (flag) {
            // 2. 关注当前博主
            Follow follow = new Follow();
            follow.setFollowUserId(id);
            follow.setUserId(userId);

            //  2.2 保存到数据库
            save(follow);
        } else {
            //  3. 取消关注
            //  3.2 从数据库取消关注
            remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", id));
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
}
