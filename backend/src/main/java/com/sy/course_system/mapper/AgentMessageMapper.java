package com.sy.course_system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.course_system.entity.AgentMessage;

@Mapper
public interface AgentMessageMapper extends BaseMapper<AgentMessage> {

    @Select("""
            SELECT *
            FROM agent_message
            WHERE session_id = #{sessionId}
              AND user_id = #{userId}
            ORDER BY create_time ASC, id ASC
            """)
    List<AgentMessage> selectBySessionIdAndUserId(@Param("sessionId") Long sessionId,
            @Param("userId") Long userId);

    @Select("""
            SELECT *
            FROM (
                SELECT *
                FROM agent_message
                WHERE session_id = #{sessionId}
                  AND user_id = #{userId}
                ORDER BY create_time DESC, id DESC
                LIMIT #{limit}
            ) recent_messages
            ORDER BY create_time ASC, id ASC
            """)
    List<AgentMessage> selectRecentBySessionIdAndUserId(@Param("sessionId") Long sessionId,
            @Param("userId") Long userId,
            @Param("limit") Integer limit);
}
