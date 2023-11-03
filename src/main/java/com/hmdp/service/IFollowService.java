package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author dy
 * @since 2023/10/16 10:06
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注 or 取关博主
     *
     * @param id
     * @param flag
     * @return
     */
    Result follow(Long id, Boolean flag);

    /**
     * 根据博主 id, 查询目前登录用户是否关注博主
     *
     * @param id
     * @return
     */
    Result isFollow(Long id);
}
