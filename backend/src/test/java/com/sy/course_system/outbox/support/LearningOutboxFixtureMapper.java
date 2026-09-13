package com.sy.course_system.outbox.support;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 仅供 Outbox 集成测试准备数据、注入数据库故障和验证持久化结果，不进入生产包。 */
@Mapper
public interface LearningOutboxFixtureMapper {

    @Select("SELECT kp_id FROM course_knowledge_point WHERE course_id=#{courseId}")
    List<Long> selectKnowledgePointIds(@Param("courseId") Long courseId);

    @Select("SELECT duration_seconds FROM video WHERE course_id=#{courseId} LIMIT 1")
    Integer selectVideoDuration(@Param("courseId") Long courseId);

    @Delete("DELETE FROM learning_outbox_task")
    int clearTasks();

    @Delete("DELETE FROM learning_behavior WHERE user_id=2 AND course_id=1")
    int clearBehaviors();

    @Update("UPDATE user SET deleted=0 WHERE id=2")
    int restoreUser();

    @Update("UPDATE course SET status=1 WHERE id=1")
    int publishCourse();

    @Update("UPDATE video SET duration_seconds=600 WHERE course_id=1")
    int resetVideoDuration();

    @Update("UPDATE user_course_relation SET learned_seconds=0,progress=0,status=0,complete_time=NULL,is_favorite=0,last_view_recorded_at=NULL WHERE user_id=2 AND course_id=1")
    int resetRelation();

    @Update("CREATE TRIGGER outbox_fail BEFORE INSERT ON learning_outbox_task FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected task failure'")
    int createOutboxFailureTrigger();

    @Update("DROP TRIGGER outbox_fail")
    int dropOutboxFailureTrigger();

    @Update("CREATE TRIGGER finish_fail BEFORE INSERT ON learning_behavior FOR EACH ROW BEGIN IF NEW.behavior_type='FINISH' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='injected finish failure'; END IF; END")
    int createFinishFailureTrigger();

    @Update("DROP TRIGGER finish_fail")
    int dropFinishFailureTrigger();

    @Update("UPDATE learning_outbox_task SET next_attempt_at=NOW(6) WHERE status='PENDING'")
    int makePendingDue();

    @Update("UPDATE course SET status=2 WHERE id=1")
    int unpublishCourse();

    @Update("UPDATE user SET deleted=1 WHERE id=2")
    int deleteUser();

    @Update("UPDATE learning_outbox_task SET next_attempt_at=TIMESTAMPADD(DAY,-1,NOW(6)) WHERE task_type='HOT_INCREMENT'")
    int prioritizeHotTasks();

    @Update("UPDATE learning_outbox_task SET attempts=20 WHERE id=#{taskId}")
    int exhaustTask(@Param("taskId") String taskId);

    @Update("UPDATE learning_outbox_task SET status='PENDING',attempts=0,next_attempt_at=NOW(6) WHERE id=#{taskId} AND status='DEAD'")
    int replayDeadTask(@Param("taskId") String taskId);

    @Update("UPDATE learning_outbox_task SET attempts=20,lease_until=TIMESTAMPADD(SECOND,-1,NOW(6)) WHERE id=#{taskId}")
    int expireExhaustedTask(@Param("taskId") String taskId);

    @Update("UPDATE learning_outbox_task SET lease_until=TIMESTAMPADD(SECOND,-1,NOW(6)),next_attempt_at=TIMESTAMPADD(DAY,-1,NOW(6)) WHERE id=#{taskId}")
    int expireTask(@Param("taskId") String taskId);

    @Select("SELECT COUNT(*) FROM learning_behavior WHERE user_id=2 AND course_id=1")
    Long countBehaviors();

    @Select("SELECT last_view_recorded_at FROM user_course_relation WHERE user_id=2 AND course_id=1")
    LocalDateTime selectLastViewTime();

    @Select("SELECT COUNT(*) FROM learning_behavior WHERE user_id=2 AND course_id=1 AND behavior_type='FINISH'")
    Long countFinishBehaviors();

    @Select("SELECT score FROM recommend_user_course_score WHERE user_id=2 AND course_id=1")
    Double selectScore();

    @Select("SELECT learned_seconds FROM user_course_relation WHERE user_id=2 AND course_id=1")
    Integer selectLearnedSeconds();

    @Select("SELECT COUNT(*) FROM learning_outbox_task")
    int countTasks();
}
