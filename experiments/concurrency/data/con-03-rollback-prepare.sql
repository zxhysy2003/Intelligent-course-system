-- CON-03 事务回滚后重试故障实验数据准备。
-- 只重置 course_concurrency 中 stu_newbie 的课程 14，不影响 CON-03 主实验的课程 10、11、12。

USE course_concurrency;

START TRANSACTION;

SET @con03_rollback_user_id := (
    SELECT id
    FROM `user`
    WHERE username = 'stu_newbie'
    LIMIT 1
);

DELETE FROM learning_behavior
WHERE user_id = @con03_rollback_user_id
  AND course_id = 14;

DELETE FROM recommend_user_course_score
WHERE user_id = @con03_rollback_user_id
  AND course_id = 14;

INSERT INTO user_course_relation (
    user_id,
    course_id,
    progress,
    learned_seconds,
    status,
    last_learn_time,
    complete_time,
    is_favorite,
    progress_seconds
)
SELECT
    @con03_rollback_user_id,
    c.id,
    0,
    0,
    0,
    NULL,
    NULL,
    0,
    0
FROM course c
WHERE @con03_rollback_user_id IS NOT NULL
  AND c.id = 14
ON DUPLICATE KEY UPDATE
    progress = VALUES(progress),
    learned_seconds = VALUES(learned_seconds),
    status = VALUES(status),
    last_learn_time = VALUES(last_learn_time),
    complete_time = VALUES(complete_time),
    is_favorite = VALUES(is_favorite),
    progress_seconds = VALUES(progress_seconds);

COMMIT;

SELECT
    u.username,
    ucr.course_id,
    ucr.progress,
    ucr.learned_seconds,
    ucr.status,
    ucr.complete_time,
    v.duration_seconds AS video_duration_seconds
FROM user_course_relation ucr
JOIN `user` u ON u.id = ucr.user_id
JOIN video v ON v.course_id = ucr.course_id
WHERE u.id = @con03_rollback_user_id
  AND ucr.course_id = 14;

SELECT COUNT(*) AS behavior_rows
FROM learning_behavior
WHERE user_id = @con03_rollback_user_id
  AND course_id = 14;
