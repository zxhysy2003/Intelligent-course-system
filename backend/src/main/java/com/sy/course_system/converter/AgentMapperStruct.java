package com.sy.course_system.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.NullValueMappingStrategy;

import com.sy.course_system.entity.AgentMessage;
import com.sy.course_system.entity.AgentSession;
import com.sy.course_system.vo.agent.AgentMessageVO;
import com.sy.course_system.vo.agent.AgentSessionVO;

/**
 * 学习助手对象映射。
 *
 * 这里只处理 Entity 与 VO 之间的纯字段搬运，会话标题、消息元数据等业务语义仍保留在 service 中。
 */
@Mapper(componentModel = "spring", nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
public interface AgentMapperStruct {
    AgentSessionVO toSessionVO(AgentSession session);

    List<AgentSessionVO> toSessionVOList(List<AgentSession> sessions);

    AgentMessageVO toMessageVO(AgentMessage message);

    List<AgentMessageVO> toMessageVOList(List<AgentMessage> messages);
}
