package com.sy.course_system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sy.course_system.entity.AgentSession;

@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSession> {

    @Select("""
            SELECT *
            FROM agent_session
            WHERE user_id = #{userId}
              AND status = 1
            ORDER BY update_time DESC, id DESC
            """)
    List<AgentSession> selectActiveByUserId(@Param("userId") Long userId);

    @Select("""
            SELECT *
            FROM agent_session
            WHERE id = #{sessionId}
              AND user_id = #{userId}
              AND status = 1
            """)
    AgentSession selectActiveByIdAndUserId(@Param("sessionId") Long sessionId, @Param("userId") Long userId);
}
