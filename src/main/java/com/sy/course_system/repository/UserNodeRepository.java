package com.sy.course_system.repository;

import java.util.List;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.sy.course_system.graph.node.UserNode;

@Repository
public interface UserNodeRepository extends Neo4jRepository<UserNode, Long> {
    @Query("""
            MERGE (u:User {id: $userId})
            """)
    void createUser(Long userId);

    @Query("""
            UNWIND $userIds AS userId
            MATCH (u:User {id: userId})
            DETACH DELETE u
            """)
    void deleteUsers(@Param("userIds") List<Long> userIds);
}
