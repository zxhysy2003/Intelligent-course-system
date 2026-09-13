package com.sy.course_system.mapper;

import org.apache.ibatis.annotations.*;
import com.sy.course_system.outbox.LearningOutboxTask;

/** Outbox 持久化入口；事务、重试策略及令牌生成由调用方负责。 */
@Mapper
public interface LearningOutboxMapper {

    @Insert("""
            INSERT INTO learning_outbox_task
            (id,source_event_id,task_type,user_id,course_id,payload,dependency_id,mastery_dependency_id)
            VALUES (#{id},#{source},#{type},#{userId},#{courseId},#{payload},#{dependency},#{masteryDependency})
            """)
    int insertTask(@Param("id") String id, @Param("source") String source, @Param("type") String type,
            @Param("userId") Long userId, @Param("courseId") Long courseId, @Param("payload") String payload,
            @Param("dependency") String dependency, @Param("masteryDependency") String masteryDependency);

    @Update("""
            UPDATE learning_outbox_task SET status='DEAD', lease_token=NULL, lease_until=NULL,
            last_error='执行租约过期且已达到尝试上限'
            WHERE status='PROCESSING' AND lease_until < NOW(6) AND attempts >= #{maxAttempts}
            """)
    int expireExhausted(@Param("maxAttempts") int maxAttempts);

    // 在调用方短事务中锁定候选，跳过其他消费者持有的锁；依赖完成或跳过后才可领取。
    @ConstructorArgs({
        @Arg(column = "id", javaType = String.class),
        @Arg(column = "task_type", javaType = String.class),
        @Arg(column = "user_id", javaType = Long.class),
        @Arg(column = "course_id", javaType = Long.class),
        @Arg(column = "payload", javaType = String.class),
        @Arg(column = "attempts", javaType = int.class),
        @Arg(column = "lease_token", javaType = String.class)
    })

    @Select("""
            SELECT t.id,t.task_type,t.user_id,t.course_id,t.payload,t.attempts,t.lease_token
            FROM learning_outbox_task t
            WHERE ((t.status='PENDING' AND t.next_attempt_at<=NOW(6))
                OR (t.status='PROCESSING' AND t.lease_until<NOW(6)))
              AND t.attempts < #{maxAttempts}
              AND (t.dependency_id IS NULL OR EXISTS
                (SELECT 1 FROM learning_outbox_task d WHERE d.id=t.dependency_id AND d.status IN ('DONE','SKIPPED')))
              AND (t.mastery_dependency_id IS NULL OR EXISTS
                (SELECT 1 FROM learning_outbox_task d WHERE d.id=t.mastery_dependency_id AND d.status IN ('DONE','SKIPPED')))
            ORDER BY t.next_attempt_at, t.id LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    LearningOutboxTask selectClaimableForUpdate(@Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE learning_outbox_task SET status='PROCESSING', attempts=#{task.attempts}, lease_token=#{task.leaseToken},
            lease_until=TIMESTAMPADD(SECOND,#{leaseSeconds},NOW(6)) WHERE id=#{task.id}
            """)
    int markProcessing(@Param("task") LearningOutboxTask task, @Param("leaseSeconds") int leaseSeconds);

    @Update("""
            UPDATE learning_outbox_task SET lease_until=TIMESTAMPADD(SECOND,#{leaseSeconds},NOW(6))
            WHERE id=#{task.id} AND status='PROCESSING' AND lease_token=#{task.leaseToken} AND lease_until>NOW(6)
            """)
    int renew(@Param("task") LearningOutboxTask task, @Param("leaseSeconds") int leaseSeconds);

    @Update("""
            UPDATE learning_outbox_task SET status=#{status},completed_at=NOW(6),lease_token=NULL,lease_until=NULL,
            last_error=NULL WHERE id=#{task.id} AND status='PROCESSING' AND lease_token=#{task.leaseToken} AND lease_until>NOW(6)
            """)
    int complete(@Param("task") LearningOutboxTask task, @Param("status") String status);

    @Update("""
            UPDATE learning_outbox_task SET status=#{status},next_attempt_at=TIMESTAMPADD(SECOND,#{delay},NOW(6)),
            last_error=#{error},lease_token=NULL,lease_until=NULL
            WHERE id=#{task.id} AND status='PROCESSING' AND lease_token=#{task.leaseToken} AND lease_until>NOW(6)
            """)
    int fail(@Param("task") LearningOutboxTask task, @Param("status") String status,
            @Param("delay") long delay, @Param("error") String error);

    @Select("""
            SELECT COUNT(*) FROM learning_outbox_task WHERE status=#{status}
            """)
    long countByStatus(@Param("status") String status);

    @Select("""
            SELECT COALESCE(TIMESTAMPDIFF(SECOND,MIN(created_at),NOW()),0)
            FROM learning_outbox_task WHERE status IN ('PENDING','PROCESSING')
            """)
    long oldestPendingSeconds();
}
