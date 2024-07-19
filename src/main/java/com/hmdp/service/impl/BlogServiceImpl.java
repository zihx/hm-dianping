package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.BlogService;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.UserService;
import com.hmdp.utils.RedisConstants;
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

/**
* @author lenovo
* @description 针对表【tb_blog】的数据库操作Service实现
* @createDate 2024-04-16 14:03:32
*/
@Service("blogService")
@Slf4j
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
    implements BlogService{

    @Resource
    private BlogMapper blogMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserService userService;
    @Resource
    private FollowMapper followMapper;

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        // 保存探店博文
        int insert = blogMapper.insert(blog);
        if (insert < 1) {
            return Result.fail("发布笔记失败");
        }
        List<Follow> follows = followMapper.selectList(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowUserId, userId));
        Long blogId = blog.getId();
        for (Follow follow : follows) {
            stringRedisTemplate.opsForZSet().add(RedisConstants.FEED_KEY + follow.getUserId(), blogId.toString(), System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blogId);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = blogMapper.selectPage(
                new Page<>(current, SystemConstants.MAX_PAGE_SIZE),
                new LambdaQueryWrapper<Blog>().orderByDesc(Blog::getLiked));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return Result.fail("暂无数据");
        }
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = blogMapper.selectById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            // 用户未登录
            return;
        }
        Long userId = userDTO.getId();
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    private void queryBlogUser(Blog blog) {
        User user = userMapper.selectById(blog.getUserId());
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score != null) {
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        } else {
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        // 左闭右闭[0, 4]
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<Long> idList = top5.stream().map(Long::parseLong).collect(Collectors.toList());
        // MySQL in查询 顺序遍历user表看id是否在idList中
        // 无法按idList中的顺序返回用户
        // 需要使用 field 特殊处理
        List<User> userList = userMapper.queryBlogLikes(idList);
        if (userList == null || userList.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        List<UserDTO> userDTOList = userList.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(userDTOList);
    }

    @Override
    public Result queryBlogByUserId(Integer current, Long userId) {
        Page<Blog> page = blogMapper.selectPage(
                new Page<>(current, SystemConstants.MAX_PAGE_SIZE),
                new LambdaQueryWrapper<Blog>().eq(Blog::getUserId, userId));
        List<Blog> records = page.getRecords();
        if (records == null || records.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        return Result.ok(records);
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(RedisConstants.FEED_KEY + userId, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        long minTime = 0;
        int count = 1;
        List<Long> blogIdList = new ArrayList<>(typedTuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            blogIdList.add(Long.parseLong(tuple.getValue()));
            long time = tuple.getScore().longValue();
            if (time == minTime) {
                count++;
            } else {
                minTime = time;
                count = 1;
            }
        }
        List<Blog> blogList = blogMapper.queryBlogByIds(blogIdList);
        if (blogList == null || blogList.isEmpty()) {
            return Result.ok();
        }
        blogList.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogList);
        scrollResult.setOffset(count);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }
}




