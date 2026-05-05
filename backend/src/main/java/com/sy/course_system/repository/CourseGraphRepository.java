package com.sy.course_system.repository;

import java.util.List;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.sy.course_system.dto.graph.CourseKnowledgePointDTO;
import com.sy.course_system.dto.recommend.CourseReadinessDTO;
import com.sy.course_system.dto.graph.KnowledgeGraphLinkDTO;
import com.sy.course_system.dto.graph.KnowledgeGraphNodeDTO;
import com.sy.course_system.graph.node.CourseNode;

public interface CourseGraphRepository extends Neo4jRepository<CourseNode, Long> {

     @Query("""
               UNWIND $courseIds AS cid
               MATCH (c:Course {id: cid})-[:HAS_KP]->(k:Knowledge)
               RETURN c.id AS courseId,
                      k.id AS id,
                      k.name AS name,
                      k.difficulty AS difficulty
               ORDER BY c.id, coalesce(k.difficulty, 99), k.name
               """)
     List<CourseKnowledgePointDTO> findCourseKnowledgePointsBatch(
               @Param("courseIds") List<Long> courseIds);


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

     // 课程知识点节点 + 掌握度
     @Query("""
               MATCH (c:Course {id: $courseId})
               MATCH (u:User {id: $userId})
               CALL {
                   WITH c, u
                   MATCH (c)-[:HAS_KP]->(k:Knowledge)
                   OPTIONAL MATCH (u)-[m:MASTERED]->(k)
                   RETURN k.id AS kpId,
                          k.name AS name,
                          k.difficulty AS difficulty,
                          coalesce(m.score, 0.0) AS mastery,
                          true AS inCourse,
                          0 AS depth
                   UNION
                   WITH c, u
                   MATCH (c)-[:HAS_KP]->(k:Knowledge)
                   MATCH p = (k)-[:PRE_REQUIRES*1..]->(pre:Knowledge)
                   WHERE length(p) <= $depth
                   WITH pre, min(length(p)) AS d
                   OPTIONAL MATCH (u)-[m:MASTERED]->(pre)
                   RETURN pre.id AS kpId,
                          pre.name AS name,
                          pre.difficulty AS difficulty,
                          coalesce(m.score, 0.0) AS mastery,
                           false AS inCourse,
                           d AS depth
               }
               WITH kpId,
                    head(collect(name)) AS name,
                    head(collect(difficulty)) AS difficulty,
                    max(mastery) AS mastery,
                    max(CASE WHEN inCourse THEN 1 ELSE 0 END) AS inCourseFlag,
                    min(depth) AS minDepth
               RETURN kpId,
                      name,
                      difficulty,
                      mastery,
                      CASE WHEN inCourseFlag = 1 THEN true ELSE false END AS inCourse,
                      CASE WHEN inCourseFlag = 1 THEN 0 ELSE minDepth END AS depth
               ORDER BY inCourse DESC, depth ASC, coalesce(difficulty, 99), name
               """)
     List<KnowledgeGraphNodeDTO> findKnowledgeGraphNodes(
               @Param("courseId") Long courseId,
               @Param("userId") Long userId,
               @Param("depth") int depth);

     // 课程内知识点之间的先修关系
     @Query("""
               MATCH (c:Course {id: $courseId})-[:HAS_KP]->(k:Knowledge)
               OPTIONAL MATCH p = (k)-[:PRE_REQUIRES*1..]->(pre:Knowledge)
               WHERE length(p) <= $depth
               WITH collect(DISTINCT k) + collect(DISTINCT pre) AS nodes
               UNWIND nodes AS n
               MATCH (n)-[:PRE_REQUIRES]->(m)
               WHERE m IN nodes
               RETURN DISTINCT n.id AS sourceId,
                               m.id AS targetId
               """)
     List<KnowledgeGraphLinkDTO> findKnowledgeGraphLinks(
               @Param("courseId") Long courseId,
               @Param("depth") int depth);

}
