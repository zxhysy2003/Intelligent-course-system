-- CON-03 三轮实验最终状态断言
-- 运行顺序必须是：准备数据 -> 课程10 SAME -> 课程10 CONFLICT -> 课程11 SAME -> 课程12 UNIQUE。

USE course_concurrency;

SET @con03_user_id := (
    SELECT id
    FROM `user`
    WHERE username = 'stu_newbie'
    LIMIT 1
);

WITH con03_actual AS (
    SELECT
        ucr.course_id,
        ucr.progress,
        ucr.learned_seconds,
        ucr.status,
        ucr.complete_time,
        COALESCE(SUM(CASE WHEN lb.behavior_type = 'STUDY' THEN 1 ELSE 0 END), 0) AS study_count,
        COALESCE(SUM(CASE WHEN lb.behavior_type = 'STUDY' THEN lb.duration ELSE 0 END), 0) AS duration_sum,
        COUNT(DISTINCT CASE WHEN lb.behavior_type = 'STUDY' THEN lb.event_id END) AS distinct_event_count,
        COALESCE(SUM(CASE WHEN lb.behavior_type = 'FINISH' THEN 1 ELSE 0 END), 0) AS finish_count
    FROM user_course_relation ucr
    LEFT JOIN learning_behavior lb
      ON lb.user_id = ucr.user_id
     AND lb.course_id = ucr.course_id
    WHERE ucr.user_id = @con03_user_id
      AND ucr.course_id IN (10, 11, 12)
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
    finish_count,
    CASE
        WHEN course_id IN (10, 11)
             AND progress = 0
             AND learned_seconds = 5
             AND status = 1
             AND complete_time IS NULL
             AND study_count = 1
             AND duration_sum = 5
             AND distinct_event_count = 1
             AND finish_count = 0
            THEN 'PASS'
        WHEN course_id = 12
             AND progress = 70
             AND learned_seconds = 500
             AND status = 1
             AND complete_time IS NULL
             AND study_count = 100
             AND duration_sum = 500
             AND distinct_event_count = 100
             AND finish_count = 0
            THEN 'PASS'
        ELSE 'FAIL'
    END AS assertion_result
FROM con03_actual
ORDER BY course_id;

-- 事件 ID 明细只输出 SAME 两轮的单行记录；必须分别保持 duration=5。
SELECT course_id, event_id, behavior_type, duration, create_time
FROM learning_behavior
WHERE user_id = @con03_user_id
  AND course_id IN (10, 11)
ORDER BY course_id, id;
