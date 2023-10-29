--  1. 参数列表
--  1.1 优惠卷 id (根据它来获得优惠卷信息)
local voucherId = ARGV[1]

--  1.2 用户 id (判断用户是否重复下单)
local userId = ARGV[2]

--  订单 id (用来后续执行订单的操作)
--local orderId = ARGV[3]


--  2. 数据 key
--  2.1 库存 key
local stockKey = 'seckill:stock:' .. voucherId

--  2.2 订单 key
local orderKey = 'seckill:order:' .. voucherId


--  3. 脚本业务
--  3.1 首先判断库存是否充足
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 3.2 库存不足, 返回 1
    return 1
end

--  3.2 判断用户是否重复下单
if (redis.call('sismember', orderKey, userId) == 1) then
    --  3.3 用户已经下过单, 返回2
    return 2
end


--  3.4 走的这里, 我们已经可以正常下单了 扣库存
redis.call('incrby', stockKey, -1)

--  3.5 下单 (保存用户)
redis.call('sadd', orderKey, userId)

--  4. 返回正常结果 0
return 0