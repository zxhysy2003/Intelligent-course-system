package com.sy.course_system.repository;

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
    
}
