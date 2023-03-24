package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private IFollowService followService;

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
        UserDTO usr = UserHolder.getUser();
        if(usr==null) return;
        Long user = UserHolder.getUser().getId();
        Double score = redisTemplate.opsForZSet().score(key, user.toString());
        blog.setIsLike(score!=null);
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
        Double exist = redisTemplate.opsForZSet().score(key, usrID.toString());
        //如果用户没有点过赞
        if(exist==null)
        {
            //将用户加入到该博客的集合里面,时间戳作为权重
            redisTemplate.opsForZSet().add(key,usrID.toString(),System.currentTimeMillis());
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
            redisTemplate.opsForZSet().remove(key,usrID.toString());
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

    @Override
    public Result getLikes(Long id) {

        //根据博客id获取前五的点赞用户id
        String key = BLOG_LIKED_KEY+id;
        Set<String> range = redisTemplate.opsForZSet().range(key, 0, 4);
        if(range ==null || range.isEmpty()){
            return Result.ok(Collections.EMPTY_LIST);
        }
        //将用户信息进行处理
        List<Long> ids = range.stream().map(Long::valueOf).collect(Collectors.toList());    //Long::valueOf将所有string类型转化为long类型
        String str = StrUtil.join(",", ids);    //用于拼接sql语句


        List<UserDTO> userDtos = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + str + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDtos);
    }

    /***
     * 保存博客功能，同时将该博客发送给所有粉丝
     * @param blog
     * @return
     */
    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean save = save(blog);
        if(save==false)
        {
            return Result.fail("笔记保存失败");
        }
        //获取当前发布笔记用户的所有粉丝
        LambdaQueryWrapper<Follow> lqw = new LambdaQueryWrapper<>();
        lqw.eq(Follow::getFollowUserId,user.getId());
        List<Follow> list = followService.list(lqw);
        //遍历所有粉丝，将博客推送给每一个粉丝,同时以时间戳为权重
        list.forEach(
                item->{
                    String key = FEED_KEY + item.getUserId();
                    redisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
                }
        );
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryFollowBlog(long lastId,int offset ) {

        //1.获取当前用户
        Long userID = UserHolder.getUser().getId();
        //2.查询收件箱
        String key = FEED_KEY + userID;
            //对所有的博客按照时间戳降序排列
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, lastId, offset, 2);
            //每个元组包括对应的值和权重
            //判空
            if(typedTuples ==null || typedTuples.isEmpty())
            {
                return Result.ok(Collections.EMPTY_LIST);
            }
        //3.解析数据：博客id，时间戳==>offset
            long maxi_time = 0;
            int ofs = 1;

            //保存这个用户当前获取的博客id
            List<Long> ids = new ArrayList<>(typedTuples.size());   //将数组的大小预先设置成和redis里面存的一样大，尽量避免扩容

        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            ids.add(Long.valueOf(typedTuple.getValue()));
            long v = typedTuple.getScore().longValue();
            if(v==maxi_time)
            {
                ofs++;
            }
            else
            {
                maxi_time=v;
                ofs=1;
            }
        }
        //4.根据id查询博客,为了防止失序，还得用之前手动拼接的方法来查询
        String strs = StrUtil.join(",");
        List<Blog> blogs = this.query().in("id", ids).last("ORDER BY FIELD(id," + strs + ")").list();

        blogs.forEach(
                item ->
                {
                    //还需要给每个博客设置点赞信息和用户信息
                    this.setUserInfo(item);
                    this.isLiked(item);
                }
        );

        //5.将用户信息和点赞信息封装进博客里面
        ScrollResult result = new ScrollResult();
        result.setList(blogs);
        result.setOffset(ofs);
        result.setMinTime(maxi_time);
        //6.将博客列表返回
        return Result.ok(result);
    }

    private void setUserInfo(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
