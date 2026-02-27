package com.sy.course_system.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.sy.course_system.dto.course.KnowledgePointOptionDTO;

@Mapper
public interface KnowledgePointMapper {

    @Select("""
            SELECT
                kp.id,
                kp.name,
                kp.difficulty,
                kp.dimension_id AS dimensionId,
                kd.name AS dimensionName
            FROM knowledge_point kp
            LEFT JOIN knowledge_dimension kd ON kd.id = kp.dimension_id
            WHERE kp.status = 1
            ORDER BY kd.sort, kd.name, kp.difficulty, kp.name
            """)
    List<KnowledgePointOptionDTO> listEnabledKnowledgePointOptions();
}
