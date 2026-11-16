-- KEYS[1]: 用户登录设备集合 Key
-- ARGV[1]: 要检查的sessionId

-- ZSET:
-- member = sessionId
-- score = 会话过期时间戳（毫秒）
-- Return 0: 当前设备未登录
-- Return 1: 当前设备已登录

local setKey = KEYS[1]
local sessionId = ARGV[1]

-- 1. 获取 Redis 当前时间（毫秒）
local time = redis.call('TIME')
local currentTime = time[1] * 1000 + math.floor(time[2] / 1000)

-- 2. 清理已经过期的设备，删除过期时间戳小于等于当前时间的设备
redis.call('ZREMRANGEBYSCORE', setKey, '-inf', currentTime)

-- 3. 判断当前设备是否已经登录
if redis.call('ZSCORE', setKey, sessionId) ~= false then
    return 1
end

return 0