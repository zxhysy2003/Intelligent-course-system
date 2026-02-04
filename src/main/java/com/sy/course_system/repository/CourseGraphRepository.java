package com.sy.course_system.repository;

import java.util.List;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.sy.course_system.dto.recommend.CourseReadinessDTO;
import com.sy.course_system.graph.node.CourseNode;
import com.sy.course_system.graph.node.KnowledgeNode;

public interface CourseGraphRepository extends Neo4jRepository<CourseNode, Long> {

     // 课程知识点
     @Query("""
               MATCH (c:Course {id: $courseId})-[:HAS_KP]->(k:Knowledge)
               RETURN DISTINCT k
               ORDER BY coalesce(k.difficulty, 99), k.name
               """)
     List<KnowledgeNode> findCourseKnowledgePoints(
               @Param("courseId") Long courseId);

     // 多跳路径
     @Query("""
               MATCH (u:User {id: $userId}),
                     (c:Course {id: $courseId})-[:HAS_KP]->(k:Knowledge)
               MATCH p = (k)-[:PRE_REQUIRES*1..3]->(pre:Knowledge)
               // 路径上的所有先修节点,都要求 have < threshold 才纳入"待学习路径"
               WHERE ALL(n IN nodes(p)[1..] WHERE
                   coalesce( [(u)-[m:MASTERED]->(n) | m.score][0], 0.0 ) < $threshold
                )
               RETURN nodes(p) AS path
               ORDER BY length(p), pre.difficulty
               LIMIT $limit
               """)
     List<List<KnowledgeNode>> findLearningPaths(
               @Param("userId") Long userId,
               @Param("courseId") Long courseId,
               @Param("threshold") Double threshold,
               @Param("limit") int limit);

     @Query("""
                   MATCH (u:User {id: $userId})
                   UNWIND $courseIds AS cid
                   MATCH (c:Course {id: cid})

                   OPTIONAL MATCH (c)-[:HAS_KP]->(k:Knowledge)
                   OPTIONAL MATCH (k)-[:PRE_REQUIRES*1..3]->(pre:Knowledge)
                   WITH u, c, COLLECT(DISTINCT pre) AS pres
                   WITH u, c, [p IN pres WHERE p IS NOT NULL] AS preList

                   UNWIND (CASE WHEN size(preList)=0 THEN [NULL] ELSE preList END) AS p
                   OPTIONAL MATCH (u)-[m:MASTERED]->(p)

                   WITH c, preList,
                        COLLECT(CASE WHEN p IS NULL THEN NULL ELSE COALESCE(m.score, 0.0) END) AS scores,
                        COLLECT(CASE WHEN p IS NULL THEN NULL
                                     ELSE {id:p.id, name:p.name, difficulty:p.difficulty, have:COALESCE(m.score,0.0), need:$threshold}
                                END) AS details

                   WITH c, preList, scores,
                        [d IN details WHERE d IS NOT NULL AND d.have < d.need] AS missing

                   WITH c,
                        CASE WHEN size(preList)=0
                             THEN 1.0
                             ELSE reduce(s=0.0, x IN [v IN scores WHERE v IS NOT NULL] | s + x) / size(preList)
                        END AS readiness,
                        missing

                   RETURN c.id AS courseId, readiness AS readiness, missing AS missing
               """)
     List<CourseReadinessDTO> getCourseReadinessBatch(@Param("userId") Long userId,
               @Param("courseIds") List<Long> courseIds,
               @Param("threshold") Double threshold);

}
