package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.USER_FOLLOW_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result isFollowed(long id) {
        System.out.println("发送是否关注请求========================================");
        Long user = UserHolder.getUser().getId();
        LambdaQueryWrapper<Follow> lqw = new LambdaQueryWrapper<>();
        lqw.eq(Follow::getFollowUserId,id);
        lqw.eq(Follow::getUserId,user);
        int count = count(lqw);

        System.out.println("该用户是否已关注该用户：  "+ count);

        return Result.ok(count>0);
    }

    @Override
    public Result follow(long id, boolean isFollow) {
        Long user = UserHolder.getUser().getId();
        LambdaQueryWrapper<Follow> lqw = new LambdaQueryWrapper<>();
        lqw.eq(Follow::getUserId,user);
        lqw.eq(Follow::getFollowUserId,id);
        int count = count(lqw);
        String key = USER_FOLLOW_KEY + user;
        if(isFollow)
        {
            //关注，添加数据
            Follow follow = new Follow();
            follow.setUserId(user);
            follow.setFollowUserId(id);
            boolean save = save(follow);
            if(save)
            {
                redisTemplate.opsForSet().add(key,String.valueOf(id));
                return Result.ok("关注成功");
            }
            return Result.fail("关注失败");
        }
        else
        {
            //取关，删除数据
            boolean remove = this.remove(lqw);
            if(remove)
            {
                redisTemplate.opsForSet().remove(key,String.valueOf(id));
                return Result.ok("已取消关注");
            }
            return Result.fail("取消关注失败");
        }
    }

    @Override
    public Result getCommons(long id) {
        //获取两个人关注的交集
        Long user = UserHolder.getUser().getId();
        String k1 = USER_FOLLOW_KEY + user;
        String k2 = USER_FOLLOW_KEY + id;
        Set<String> intersect = redisTemplate.opsForSet().intersect(k1, k2);

        if(intersect ==null || intersect.isEmpty())
        {
            return Result.ok(Collections.EMPTY_LIST);
        }

            //获取两个人的交集之后，将用户id先转换成long类型的，方便后面查询用户
        List<Long> collect = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
            //因为需要显示用户的头像等信息，所以返回userDto对象
        List<UserDTO> ret = userService.listByIds(collect).stream()
                .map(usr -> BeanUtil.copyProperties(usr, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(ret);
    }
}
