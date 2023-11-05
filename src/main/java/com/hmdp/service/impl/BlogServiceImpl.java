package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;


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

        //  查询 blog 是否被当前登录用户点赞
        isBlogLiked(blog);

        return Result.ok(blog);
    }

    /**
     * 查询 blog 是否被当前登录用户点赞
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

     /**
     * 将用户的个人信息和博客捆绑显示(也就是该博客显示的有发布博客的作者)
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    /**
     * 查询给博客点赞的前五名用户用户
     *
     * @param id
     * @return
     */
    public Result queryBlogLikes(Long id) {
        //  根据博客的 id, 查询给本篇博客点赞的用户

        //  1. 首先获取 key
        String key = BLOG_LIKED_KEY + id;

        //  2. 在 Redis 中查询  (查询前 5 名)
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

    /**
     * 保存博客 + 给粉丝信箱推送消息
     *
     * @param blog
     * @return
     */
    public Result saveBlog(Blog blog) {

        //  1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        //  2. 给博客添加所属者 id
        blog.setUserId(user.getId());

        // 3. 保存探店博文
        if (!save(blog)) {
            return Result.fail("报错博客失败");
        }

        //  4.1 推送博客到所有粉丝
        //  4.2 查询该博主所拥有的粉丝数量
        Long userId = user.getId();
        List<Follow> follows = followService.query().eq("follow_user_id", userId).list();

        //  4.3 给所有粉丝发消息
        for (Follow follow : follows) {
//            Long followId = follow.getId();
            Long followId = follow.getUserId();
            String key = FEED_KEY + followId;
//            stringRedisTemplate.opsForZSet().add(key, followId.toString(), System.currentTimeMillis());
            //                                      这里应该传相应博客的 id
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());

            log.info("博主 {} 发送博客 {}, 推送给: {} ", userId, blog.getId(), followId);
        }

        // 返回id
        return Result.ok(blog.getId());

    }

    /**
     * 好友关注, 滚动分页查询
     *
     * @param max
     * @param offset
     * @return
     */
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //  1. 获取当前用户
        Long userId = UserHolder.getUser().getId();

        //  2. 获取当前用户信箱
        //  2. 取出信箱中的数据 (包括收到的博客 id, 和 该博客的 时间戳)
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);

        //  3. 判断信箱中的数据是否为空
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        //  4. 解析信箱中的数据 (做一些其他的处理)
        long minTime = 0;
        int os = 1;
        //  初始化大小, 防止后续扩容影响性能
        List<Long> ids = new ArrayList<>(typedTuples.size());



        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {

            ids.add(Long.valueOf(typedTuple.getValue()));

            long time = typedTuple.getScore().longValue();
            if (time == minTime) {
                os++;
            } else {
                os = 1;
                minTime = time;
            }
        }

        //  5. 根据 ids 查询对应的 博客 (确保有顺序的执行)

//        String idStr = StrUtil.join(",", ids);
//        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        String idStr = StrUtil.join(",", ids);

        log.info("收到这些人的推送: {}",idStr);

        List<Blog> blogList = query().in("id", ids).last("ORDER BY FIELD(id, " + idStr+ " )").list();

        //  6. 完善对应的博客信息
        for (Blog blog : blogList) {
            //  查询 blog 相关用户, 将用户信息封装到 blog 中
            queryBlogUser(blog);

            //  查询 blog 是否被当前登录用户点赞
            isBlogLiked(blog);
        }

        //  7. 封装数据并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogList);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);


        return Result.ok(scrollResult);
    }
}
