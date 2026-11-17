-- KEYS[1]: 用户登录设备集合 Key
-- ARGV[1]: 当前设备的 SessionId
-- ARGV[2]: 最大允许登录设备数量
-- ARGV[3]: 当前设备会话过期时间戳（毫秒）

-- ZSET:
-- member = sessionId
-- score = 会话过期时间戳（毫秒）

local setKey = KEYS[1]
local sessionId = ARGV[1]
local sessionLimit = tonumber(ARGV[2])
local expireTimeStamp = tonumber(ARGV[3])

-- 1. 获取 Redis 当前时间（毫秒）
local time = redis.call('TIME')
local currentTime = time[1] * 1000 + math.floor(time[2] / 1000)

-- 2. 清理已经过期的设备，删除过期时间戳小于等于当前时间的设备
redis.call('ZREMRANGEBYSCORE', setKey, '-inf', currentTime)

-- 3. 判断当前设备是否已经登录
if redis.call('ZSCORE', setKey, sessionId) ~= false then
    return 1
end

-- 4. 获取当前已登录设备数量
local currentSize = redis.call('ZCARD', setKey)

-- 5. 判断是否超过设备数量限制
if currentSize >= sessionLimit then
    return 2
end

-- 6. 添加当前设备 SessionId
redis.call('ZADD', setKey, expireTimeStamp, sessionId)

return 0