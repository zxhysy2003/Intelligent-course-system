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

    // =========================
    // 知识点节点 upsert
    // =========================
    @Query("""
        MERGE (k:Knowledge {id: $id})
        ON CREATE SET
            k.name = $name,
            k.difficulty = $difficulty
        ON MATCH SET
            k.name = $name,
            k.difficulty = $difficulty
        """)
    void saveOrUpdateKnowledge(
            @Param("id") Long id,
            @Param("name") String name,
            @Param("difficulty") Integer difficulty);

    // =========================
    // 创建 PRE_REQUIRES 关系
    // a -> b 表示 a 需要 b
    // =========================
    @Query("""
        MATCH (a:Knowledge {id: $aId})
        MATCH (b:Knowledge {id: $bId})
        MERGE (a)-[:PRE_REQUIRES]->(b)
        """)
    void createPrerequisite(
            @Param("aId") Long knowledgeId,
            @Param("bId") Long prerequisiteId);

    // =========================
    // 标记用户掌握
    // =========================
    @Query("""
        MATCH (u:User {id: $userId})
        MATCH (k:Knowledge {id: $kpId})
        MERGE (u)-[:MASTERED]->(k)
        """)
    void markUserMastered(
            @Param("userId") Long userId,
            @Param("kpId") Long kpId);

    
    // =========================
    // 批量标记用户掌握
    // =========================
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

    // =========================
    // 查询缺失先修
    // =========================
    @Query("""
        MATCH (u:User {id: $userId}),
              (k:Knowledge {id: $kpId})
        MATCH (k)-[:PRE_REQUIRES]->(pre)
        WHERE NOT (u)-[:MASTERED]->(pre)
        RETURN DISTINCT pre
        """)
    List<KnowledgeNode> findMissingPrerequisites(
            @Param("userId") Long userId,
            @Param("kpId") Long kpId);

    // =========================
    // 推荐后继知识点
    // =========================
    @Query("""
        MATCH (u:User {id: $userId}),
              (k:Knowledge {id: $kpId})
        MATCH (next)-[:PRE_REQUIRES]->(k)
        WHERE NOT (u)-[:MASTERED]->(next)
        RETURN DISTINCT next
        """)
    List<KnowledgeNode> findNextToLearn(
            @Param("userId") Long userId,
            @Param("kpId") Long kpId);

    // =========================
    // 按难度推荐后继
    // =========================
    @Query("""
        MATCH (u:User {id: $userId}),
              (k:Knowledge {id: $kpId})
        MATCH (next)-[:PRE_REQUIRES]->(k)
        WHERE next.difficulty <= k.difficulty + 1
          AND NOT (u)-[:MASTERED]->(next)
        RETURN DISTINCT next
        ORDER BY next.difficulty
        LIMIT $limit
        """)
    List<KnowledgeNode> recommendNextByDifficulty(
            @Param("userId") Long userId,
            @Param("kpId") Long kpId,
            @Param("limit") int limit);

    // =========================
    // 多跳路径推荐
    // =========================
    @Query("""
        MATCH (u:User {id: $userId}),
              (start:Knowledge {id: $kpId})

        MATCH p = (start)-[:PRE_REQUIRES*1..3]->(pre)

        WHERE
          ALL(n IN nodes(p)[1..] WHERE NOT (u)-[:MASTERED]->(n))

        RETURN nodes(p) AS path
        ORDER BY length(p)
        LIMIT 5
        """)
    List<List<KnowledgeNode>> recommendLearningPaths(
            @Param("userId") Long userId,
            @Param("kpId") Long kpId);

}
