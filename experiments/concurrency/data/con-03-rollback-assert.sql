-- CON-03 事务回滚后重试故障实验断言。
-- FAIL 阶段之后必须得到 ROLLED_BACK；RECOVER 阶段之后必须得到 RECOVERED。

USE course_concurrency;

SET @con03_rollback_user_id := (
    SELECT id
    FROM `user`
    WHERE username = 'stu_newbie'
    LIMIT 1
);

WITH rollback_actual AS (
    SELECT
        ucr.course_id,
        ucr.progress,
        ucr.learned_seconds,
        ucr.status,
        ucr.complete_time,
        COALESCE(SUM(CASE WHEN lb.behavior_type = 'STUDY' THEN 1 ELSE 0 END), 0) AS study_count,
        COALESCE(SUM(CASE WHEN lb.behavior_type = 'STUDY' THEN lb.duration ELSE 0 END), 0) AS duration_sum,
        COUNT(DISTINCT CASE WHEN lb.behavior_type = 'STUDY' THEN lb.event_id END) AS distinct_event_count,
        MAX(CASE WHEN lb.behavior_type = 'STUDY' THEN lb.event_id END) AS event_id,
        COALESCE(SUM(CASE WHEN lb.behavior_type = 'FINISH' THEN 1 ELSE 0 END), 0) AS finish_count
    FROM user_course_relation ucr
    LEFT JOIN learning_behavior lb
      ON lb.user_id = ucr.user_id
     AND lb.course_id = ucr.course_id
    WHERE ucr.user_id = @con03_rollback_user_id
      AND ucr.course_id = 14
    GROUP BY
        ucr.course_id,
        ucr.progress,
        ucr.learned_seconds,
        ucr.status,
        ucr.complete_time
)
SELECT
    course_id,
    progress,
    learned_seconds,
    status,
    complete_time,
    study_count,
    duration_sum,
    distinct_event_count,
    event_id,
    finish_count,
    CASE
        WHEN progress = 0
             AND learned_seconds = 0
             AND status = 0
             AND complete_time IS NULL
             AND study_count = 0
             AND duration_sum = 0
             AND distinct_event_count = 0
             AND event_id IS NULL
             AND finish_count = 0
            THEN 'ROLLED_BACK'
        WHEN progress = 0
             AND learned_seconds = 5
             AND status = 1
             AND complete_time IS NULL
             AND study_count = 1
             AND duration_sum = 5
             AND distinct_event_count = 1
             AND event_id = 'con03-rollback-event'
             AND finish_count = 0
            THEN 'RECOVERED'
        ELSE 'FAIL'
    END AS assertion_state
FROM rollback_actual;

SELECT course_id, event_id, behavior_type, duration, create_time
FROM learning_behavior
WHERE user_id = @con03_rollback_user_id
  AND course_id = 14
ORDER BY id;
