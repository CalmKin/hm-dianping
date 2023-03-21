-- 如果获取锁的值和自己的用户id能对上，那么就可以执行删除锁操作
-- 这里的 KEYS[1] 就是锁的key，这里的ARGV[1] 就是当前线程标示
if(redis.call("GET",KEYS[1]) == ARGV[1])  then
    -- 一致，则删除锁
    return redis.call("DEL",KEYS[1])
end
-- 不一致，则直接返回
return 0