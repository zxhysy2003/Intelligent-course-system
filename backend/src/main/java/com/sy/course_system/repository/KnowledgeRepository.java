package com.sy.course_system.repository;

import java.util.List;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import com.sy.course_system.dto.AbilityDimensionScoreDTO;
import com.sy.course_system.graph.node.KnowledgeNode;

public interface KnowledgeRepository extends Neo4jRepository<KnowledgeNode, Long> {

    @Query("""
        MATCH (u:User {id: $userId})-[m:MASTERED]->(k:Knowledge)
        WITH coalesce(k.dimension, '未分类') AS dimension,
             avg(coalesce(m.score, 0.0)) AS avgScore
        RETURN dimension AS dimension,
               round(avgScore * 100.0, 2) AS value
        ORDER BY dimension
        """)
    List<AbilityDimensionScoreDTO> getAbilityDimensionScores(@Param("userId") Long userId);

    // 完课后批量标记用户掌握课程知识点。
    @Query("""
        MATCH (u:User {id: $userId})
        UNWIND $kpIds AS kpId
        MATCH (k:Knowledge {id: kpId})
        MERGE (u)-[m:MASTERED]->(k)
        SET m.score = $score,
            m.updatedAt = datetime()
        """)
    void markUserMasteredBatch(
            @Param("userId") Long userId,
            @Param("kpIds") List<Long> kpIds,
            @Param("score") Double score);

}
