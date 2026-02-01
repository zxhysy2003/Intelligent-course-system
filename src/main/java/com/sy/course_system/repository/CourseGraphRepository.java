package com.sy.course_system.repository;

import java.util.List;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.sy.course_system.graph.node.CourseNode;
import com.sy.course_system.graph.node.KnowledgeNode;

public interface CourseGraphRepository extends Neo4jRepository<CourseNode, Long> {

    // 课程知识点
    @Query("""
            MATCH (c:Course {id: $courseId})-[:HAS_KP]->(k:Knowledge)
            RETURN DISTINCT k
            ORDER BY k.difficulty
            """)
    List<KnowledgeNode> findCourseKnowledgePoints(
            @Param("courseId") Long courseId);

    // 用户缺失先修
    @Query("""
            MATCH (u:User {id: $userId}),
                  (c:Course {id: $courseId})-[:HAS_KP]->(k:Knowledge)
            MATCH (k)-[:PRE_REQUIRES*1..3]->(pre:Knowledge)
            WHERE NOT (u)-[:MASTERED]->(pre)
            RETURN DISTINCT pre
            ORDER BY pre.difficulty
            LIMIT $limit
            """)
    List<KnowledgeNode> findMissingPrerequisites(
            @Param("userId") Long userId,
            @Param("courseId") Long courseId,
            @Param("limit") int limit);

    // 多跳路径
    @Query("""
            MATCH (u:User {id: $userId}),
                  (c:Course {id: $courseId})-[:HAS_KP]->(k:Knowledge)
            MATCH p = (k)-[:PRE_REQUIRES*1..3]->(pre:Knowledge)
            WHERE ALL(n IN nodes(p)[1..] WHERE NOT (u)-[:MASTERED]->(n))
            RETURN nodes(p) AS path
            ORDER BY length(p)
            LIMIT $limit
            """)
    List<List<KnowledgeNode>> findLearningPaths(
            @Param("userId") Long userId,
            @Param("courseId") Long courseId,
            @Param("limit") int limit);

}
