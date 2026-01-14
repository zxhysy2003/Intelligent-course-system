package com.sy.course_system.repository;

import java.util.List;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sy.course_system.dto.LearningPathDTO;
import com.sy.course_system.graph.node.CourseNode;

@Repository
public interface LearningPathRepository extends Neo4jRepository<CourseNode, Long> {
    // 找出所有学过这门课的人，看看他们之后还学了什么课，推荐最受欢迎的几个后续课程
    @Query("""
            MATCH (u:User)-[r1:LEARN]->(from:Course {id:$courseId})
            MATCH (u)-[r2:LEARN]->(to:Course)
            WHERE r1.type = 'FINISH'
            AND r2.time > r1.time
            AND to.id <> from.id
            WITH
                from, to, count(*) AS support
            WHERE support >= 2
            RETURN
                from.id AS fromId,
                from.name AS fromName,
                to.id AS toId,
                to.name AS toName,
                support
            ORDER BY support DESC
            LIMIT $limit;
            """)
    List<LearningPathDTO> recommendPath(@Param("courseId") Long courseId,
                                        @Param("limit") int limit);
}
