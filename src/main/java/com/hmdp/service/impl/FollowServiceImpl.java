package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

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

    @Override
    public Result isFollowed(long id) {
        Long user = UserHolder.getUser().getId();
        LambdaQueryWrapper<Follow> lqw = new LambdaQueryWrapper<>();
        lqw.eq(Follow::getUserId,user);
        lqw.eq(Follow::getFollowUserId,id);
        int count = count(lqw);
        return Result.ok(count>0);
    }

    @Override
    public Result follow(long id, boolean isFollow) {
        Long user = UserHolder.getUser().getId();
        LambdaQueryWrapper<Follow> lqw = new LambdaQueryWrapper<>();
        lqw.eq(Follow::getUserId,user);
        lqw.eq(Follow::getFollowUserId,id);
        int count = count(lqw);
        if(isFollow)
        {
            //关注，添加数据
            Follow follow = new Follow();
            follow.setUserId(user);
            follow.setFollowUserId(id);
           save(follow);
            return Result.ok("关注成功");
        }
        else
        {
            //取关，删除数据
            this.remove(lqw);
            return Result.ok("已取消关注");
        }
    }
}
