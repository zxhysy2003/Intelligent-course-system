package com.sy.course_system.outbox;

import java.util.Map;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningMasteryUpdater {
    private final Neo4jClient neo4j;
    public LearningMasteryUpdater(Neo4jClient neo4j) { this.neo4j = neo4j; }

    @Transactional(transactionManager = "neo4jTransactionManager")
    public void update(Long userId, LearningOutboxPayload payload) {
        // 先写再删临时属性，节点写锁保留至事务结束，串行化同一用户的全部掌握度更新。
        long users = neo4j.query("""
                MATCH (u:User {id:$userId}) SET u._learningOutboxLock = true
                REMOVE u._learningOutboxLock RETURN count(u) AS n
                """).bind(userId).to("userId").fetchAs(Long.class).one().orElse(0L);
        if (users != 1) throw new IllegalStateException("Neo4j 用户缺失或重复: " + userId);
        long updated = neo4j.query("""
                MATCH (u:User {id:$userId})
                UNWIND $kpIds AS kpId
                MATCH (k:Knowledge {id:kpId})
                MERGE (u)-[m:MASTERED]->(k)
                WITH m, (m.score IS NULL OR m.score < $score) AS improved
                SET m.updatedAt = CASE WHEN improved THEN localdatetime($finishedAt) ELSE m.updatedAt END,
                    m.score = CASE WHEN improved THEN $score ELSE m.score END
                RETURN count(m) AS n
                """).bindAll(Map.of("userId", userId, "kpIds", payload.knowledgePointIds(),
                    "score", payload.mastery(), "finishedAt", payload.finishedAt().toString()))
                .fetchAs(Long.class).one().orElse(0L);
        // 抛异常回滚整批，防止部分节点缺失被当成成功。
        if (updated != payload.knowledgePointIds().size()) {
            throw new IllegalStateException("Neo4j 知识点缺失或重复");
        }
    }
}
