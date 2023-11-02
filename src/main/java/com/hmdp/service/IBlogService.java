package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author dy
 * @since 2023/10/16 10:06
 */
public interface IBlogService extends IService<Blog> {

    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    /**
     * 给博客点赞
     *
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 查询给博客点赞的用户
     *
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);
}
