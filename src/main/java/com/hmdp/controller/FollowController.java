package com.hmdp.controller;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {

    @Autowired
    private IFollowService service;

    /**请求地址：http://127.0.0.1/api/follow/2/true
     *
     */
     @PutMapping("/{followId}/{isFollow}")
    public Result follow(@PathVariable("followId") long id,@PathVariable("isFollow") boolean isFollow)
     {
        return service.follow(id,isFollow);
     }


    @GetMapping("/or/not/{id}")
    public Result isFollowed(@PathVariable("id") long id)
    {
        return service.isFollowed(id);
    }

    /**
     * 查询当前用户和正在浏览用户的共同关注
     * 因为涉及求交集，所以改造之前的关注业务
     * http://127.0.0.1/api/follow/common/
     */
    @GetMapping("/common/{id}")
    public  Result getCommons(@PathVariable("id") long id)
    {
        return service.getCommons(id);
    }


}
