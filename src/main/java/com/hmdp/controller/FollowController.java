package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author dy
 * @since 2023/10/16 10:06
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Resource
    private IFollowService followService;

    /**
     * 关注 or 取关博主
     *
     * @param id
     * @param flag
     * @return
     */
    @PutMapping("/{id}/{flag}")
    public Result follow(@PathVariable Long id, @PathVariable Boolean flag) {

        return followService.follow(id, flag);
    }

    /**
     * 根据博主 id, 查询目前登录用户是否关注博主
     *
     * @param id
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result follow(@PathVariable Long id) {

        return followService.isFollow(id);
    }

    /**
     * 查询共同关注
     *
     * @param id
     * @return
     */
    @GetMapping("/common/{id}")
    public Result followCommon(@PathVariable Long id) {
        return followService.followCommon(id);
    }


}
