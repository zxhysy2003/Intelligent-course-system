package com.sy.course_system.outbox;

import java.util.List;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;

@Component
public class LearningHotUpdater {
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local hotType = redis.call('TYPE', KEYS[1]).ok
            local seenType = redis.call('TYPE', KEYS[2]).ok
            if hotType ~= 'none' and hotType ~= 'zset' then return redis.error_reply('invalid hot key type') end
            if seenType ~= 'none' and seenType ~= 'string' then return redis.error_reply('invalid receipt type') end
            local delta = tonumber(ARGV[1])
            if not delta or delta ~= delta or delta == math.huge or delta == -math.huge then
                return redis.error_reply('invalid hot delta')
            end
            if redis.call('EXISTS', KEYS[2]) == 1 then return 0 end
            redis.call('ZINCRBY', KEYS[1], ARGV[1], ARGV[2])
            redis.call('SET', KEYS[2], '1')
            return 1
            """, Long.class);
    private final RedisTemplate<String, Object> redis;
    public LearningHotUpdater(RedisTemplate<String, Object> redis) { this.redis = redis; }

    public void increment(String taskId, Long courseId, double delta) {
        if (!Double.isFinite(delta) || delta < 0) throw new IllegalArgumentException("无效热度增量");
        // 使用热榜原有 value 序列化器生成 member，Lua 参数本身按字符串传输。
        // RedisConfiguration 配置的是支持 Object 的 GenericJackson2JsonRedisSerializer，
        // 但 getValueSerializer() 的返回类型为 RedisSerializer<?>，无法保留该泛型信息。
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> valueSerializer = (RedisSerializer<Object>) redis.getValueSerializer();
        byte[] memberBytes = valueSerializer.serialize(courseId);
        if (memberBytes == null) throw new IllegalStateException("课程 ID 序列化失败");
        String member = new String(memberBytes, java.nio.charset.StandardCharsets.UTF_8);
        Long result = redis.execute(SCRIPT, new StringRedisSerializer(),
                new org.springframework.data.redis.serializer.GenericToStringSerializer<>(Long.class),
                List.of("course:hot", "learning:outbox:hot:" + taskId), Double.toString(delta), member);
        if (result == null) throw new IllegalStateException("热度脚本未返回结果");
    }
}
