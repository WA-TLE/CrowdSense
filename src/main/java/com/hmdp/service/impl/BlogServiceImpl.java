package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author dy
 * @since 2023/10/16 10:06
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 分页查询
     *
     * @param current
     * @return
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    /**
     * 根据 id 查询
     *
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);

        if (blog == null) {
            return Result.fail("博客不存在哦~");
        }

        //  查询 blog 相关用户, 将用户信息封装到 blog 中
        queryBlogUser(blog);

        //  判断 blog 是否被点赞
        isBlogLiked(blog);

        return Result.ok(blog);
    }

    /**
     * 查询 blog 是否被点赞
     *
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();

        if (user == null) {
            //  用户未登录, 无序查询点赞列表
            return;
        }
        //  1. 获取登录用户
        Long userId = user.getId();

        //  2. 判断当前用户是否点过赞
        String key = BLOG_LIKED_KEY + blog.getId();
        //  查询 Redis 中是否有相应数据
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        blog.setIsLike(BooleanUtil.isTrue(score != null));

    }

    /**
     * 给博客点赞
     *
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {

        //  1. 获取登录用户
        Long userId = UserHolder.getUser().getId();

        //  2. 判断当前用户是否点过赞
        String key = BLOG_LIKED_KEY + id;
        //  查询 Redis 中是否有相应数据
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            //  3. 未点赞, 执行点赞操作

            //  操作数据库
            // 修改点赞数量
            boolean isSuccess = update().setSql("liked = liked + 1")
                    .eq("id", id).update();

            if (isSuccess) {
                //  操作 Redis
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }


        } else {
            //  4. 已点赞, 取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1")
                    .eq("id", id).update();

            if (isSuccess) {
                //  操作 Redis
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }

        }


        return Result.ok();
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 查询给博客点赞的用户
     *
     * @param id
     * @return
     */
    public Result queryBlogLikes(Long id) {
        //  1. 首先获取 key
        String key = BLOG_LIKED_KEY + id;

        //  2. 在 Redis 中查询
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        //  3. 排除无人给他点赞
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        //  4. 解析出用户的 id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());

        //  4.2 为了使点赞顺序一致, 我们可以改进下我们的 SQL 语句 (in 语句)
        String idStr = StrUtil.join(",", ids);

        //  5. 根据 id 查询用户
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id, " + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());


        return Result.ok(userDTOS);
    }
}
