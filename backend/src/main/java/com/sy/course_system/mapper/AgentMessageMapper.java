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

    /*
     * 先按倒序截取最近 N 条，再在外层恢复为正序。
     * 直接正序 LIMIT 会拿到最早 N 条；直接倒序返回又不适合作为 LLM 对话历史。
     */
    @Select("""
            SELECT *
            FROM (
                SELECT *
                FROM agent_message
                WHERE session_id = #{sessionId}
                  AND user_id = #{userId}
                  AND id <= #{messageId}
                ORDER BY create_time DESC, id DESC
                LIMIT #{limit}
            ) recent_messages
            ORDER BY create_time ASC, id ASC
            """)
    List<AgentMessage> selectRecentUntilMessageBySessionIdAndUserId(@Param("sessionId") Long sessionId,
            @Param("userId") Long userId,
            @Param("messageId") Long messageId,
            @Param("limit") Integer limit);

    @Select("""
            SELECT *
            FROM agent_message
            WHERE user_id = #{userId}
              AND role = #{role}
              AND client_message_id = #{clientMessageId}
            LIMIT 1
            """)
    AgentMessage selectByUserIdRoleAndClientMessageId(@Param("userId") Long userId,
            @Param("role") String role,
            @Param("clientMessageId") String clientMessageId);
}
