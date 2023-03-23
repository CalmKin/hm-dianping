package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Resource
    private IUserService userService;
    @Override
    public Result queryBlog(Long blogId) {
        LambdaQueryWrapper<Blog> lqw = new LambdaQueryWrapper<>();
        lqw.eq(Blog::getId,blogId);
        Blog blog = getOne(lqw);
        this.setUserInfo(blog);
        this.isLiked(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(
                blog -> {
                    this.setUserInfo(blog);
                    this.isLiked(blog);
                }
        );
        return Result.ok(records);
    }

    public void isLiked(Blog blog)
    {
        Long id = blog.getId();
        String key = BLOG_LIKED_KEY + id;
        Long user = UserHolder.getUser().getId();
        Boolean liked = redisTemplate.opsForSet().isMember(key, user.toString());
        blog.setIsLike(BooleanUtil.isTrue(liked));
    }

    @Override
    public Result likeBlog(Long id) {

        Blog blog = this.getById(id);
        if(blog==null)
        {
            return Result.fail("博客不存在");
        }

        //判断用户是否点过赞
            //1.获取用户的id
        Long usrID = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Boolean exist = redisTemplate.opsForSet().isMember(key, usrID.toString());
        //如果用户没有点过赞
        if(!exist)
        {
            //将用户加入到该博客的集合里面
            redisTemplate.opsForSet().add(key,usrID.toString());
            //博客点赞数+1
            boolean succ = update().setSql("liked = liked + 1").eq("id", id).update();
            if(succ)
            {
                return Result.ok("点赞成功");
            }
            else
            {
                return Result.fail("点赞失败");
            }
        }
        else
        {
            //否则将用户移出该博客的点赞集合
            redisTemplate.opsForSet().remove(key,usrID.toString());
            //博客点赞数-1
            boolean succ = update().setSql("liked = liked - 1").eq("id", id).update();
            if(succ)
            {
                return Result.ok("已取消点赞");
            }
            else
            {
                return Result.fail("取消点赞失败");
            }
        }

    }

    private void setUserInfo(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
