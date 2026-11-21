-- CAS 的替换刷新令牌并且防重放检查
-- 返回值含义：-1: refresh token 无效 0: refresh token 重复使用 1: refresh token 检验通过
-- 参数含义：KEYS[1]: sessionSetKey KEY[2]: oldRefTokenKey KEY[3]: curRefTokenKey
-- ARGV[1]: sessionId ARGV[2]: oldHash ARGV[3]: newHash
-- 返回 0 的时候就是发生了重返攻击，需要应用层手动销毁会话
local sessionSetKey = KEYS[1]
local oldRefTokenKey = KEYS[2]
local curRefTokenKey = KEYS[3]

local sessionId = ARGV[1]
local oldHash = ARGV[2]
local newHash = ARGV[3]

-- 1. 检查 oldKey 是否存在
--if redis.call('GET',oldRefTokenKey) == false then
--    return 1 -- oldKey 不存在，refresh token 未使用过, 检验通过
--end
if redis.call('GET',oldRefTokenKey) == oldHash then
    redis.call('DEL',oldRefTokenKey)
    return 0 -- oldKey 存在且值匹配，refresh token 重复使用
end
-- 2. 检查refreshKey的有效性
if redis.call('ZSCORE',sessionSetKey, sessionId) == false or redis.call('GET',curRefTokenKey) ~= oldHash  then
    return -1 -- refresh token 无效
end
-- 2. 此时oldKey已经存在了，检查 oldKey 的值是否与传入的 oldHash 匹配


-- 此时sessionId存在，且curRefTokenKey存的就是传入的refreshToken，且当前的refreshToken未被使用过，说明检验通过，返回1
-- 将当前的refreshToken存入oldKey
local remainTTL = redis.call('PTTL',curRefTokenKey);
redis.call('SET',oldRefTokenKey, oldHash, 'PX', remainTTL)
-- 替换为新的refreshToken
redis.call('SET',curRefTokenKey, newHash, 'KEEPTTL')
return 1