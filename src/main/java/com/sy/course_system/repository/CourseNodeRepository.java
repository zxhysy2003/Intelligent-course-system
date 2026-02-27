package com.sy.course_system.repository;

import java.util.List;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import com.sy.course_system.graph.node.CourseNode;

@Repository
public interface CourseNodeRepository extends Neo4jRepository<CourseNode, Long> {
    
    @Query("""
            MERGE (c:Course {id: $courseId})
            ON CREATE SET c.title = $title
            """)
    void createCourse(Long courseId, String title);

    @Query("""
            MATCH (c:Course {id: $courseId})
            WITH c
            UNWIND $knowledgePointIds AS kpId
            MATCH (kp:Knowledge {id: kpId})
            MERGE (c)-[:HAS_KP]->(kp)
            """)
    void bindKnowledgePoints(Long courseId, List<Long> knowledgePointIds);

    @Query("""
            MERGE (c:Course {id: $courseId})
            SET c.title = $title
            """)
    void upsertCourseTitle(Long courseId, String title);

    @Query("""
            MATCH (c:Course {id: $courseId})
            OPTIONAL MATCH (c)-[r:HAS_KP]->(:Knowledge)
            DELETE r
            """)
    void clearKnowledgePoints(Long courseId);
    
    @Query("""
            MATCH (c:Course {id: $courseId})
            DETACH DELETE c
            """)
    void deleteCourseGraph(Long courseId);

    @Query("""
            UNWIND $courseIds AS courseId
            MATCH (c:Course {id: courseId})
            DETACH DELETE c
            """)
    void deleteCourseGraphs(List<Long> courseIds);
}
