-- KEYS[1]: 用户登录设备集合 Key
-- ARGV[1]: 当前设备的 SessionId
-- ARGV[2]: 最大允许登录设备数量

local setKey = KEYS[1]
local sessionId = ARGV[1]
local sessionLimit = tonumber(ARGV[2])

-- 1. 判断当前设备是否已经登录
if redis.call('SISMEMBER', setKey, sessionId) == 1 then
    return 1
end

-- 2. 获取当前已登录设备数量
local currentSize = redis.call('SCARD', setKey)

-- 3. 判断是否超过设备数量限制
if currentSize >= sessionLimit then
    return 2
end

-- 4. 添加当前设备 SessionId
redis.call('SADD', setKey, sessionId)

return 0