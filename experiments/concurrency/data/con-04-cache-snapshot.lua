-- 只读取 shell 从实验数据库解析出的 cache/version key；不修改 TTL、版本或缓存内容。
-- 一次脚本提供同一时刻的元数据视图，不输出推荐 items 或用户隐私。
if #KEYS == 0 or #KEYS % 2 ~= 0 then
    return redis.error_reply('cache/version key pairs required')
end
local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local result = {sampledAt = now, total = #KEYS / 2, fresh = 0, stale = 0,
    missing = 0, invalid = 0, logicalTtlMinutes = {}, entries = {}}
for i = 1, #KEYS, 2 do
    local key = KEYS[i]
    local raw = redis.call('GET', key)
    local version = tonumber(redis.call('GET', KEYS[i + 1]) or '0')
    local row = {key = key, physicalTtlMs = redis.call('PTTL', key)}
    if not raw then
        row.state = 'MISSING'
        result.missing = result.missing + 1
    else
        local ok, entry = pcall(cjson.decode, raw)
        if not ok or type(entry) ~= 'table' or entry.schemaVersion ~= 2
            or type(entry.data) ~= 'table'
            or type(entry.generatedAt) ~= 'number' or type(entry.logicalExpireAt) ~= 'number'
            or type(entry.userVersion) ~= 'number' or not version then
            row.state = 'INVALID'
            result.invalid = result.invalid + 1
        else
            row.generatedAt = entry.generatedAt
            row.logicalExpireAt = entry.logicalExpireAt
            row.userVersion = entry.userVersion
            row.currentUserVersion = version
            row.remainingLogicalMs = entry.logicalExpireAt - now
            row.logicalTtlMinutes = (entry.logicalExpireAt - entry.generatedAt) / 60000
            local bucket = tostring(row.logicalTtlMinutes)
            result.logicalTtlMinutes[bucket] = (result.logicalTtlMinutes[bucket] or 0) + 1
            if entry.logicalExpireAt <= now or entry.userVersion ~= version then
                row.state = 'STALE'
                result.stale = result.stale + 1
            else
                row.state = 'FRESH'
                result.fresh = result.fresh + 1
            end
        end
    end
    table.insert(result.entries, row)
end
return cjson.encode(result)
