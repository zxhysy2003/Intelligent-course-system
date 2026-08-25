-- CON-03 STUDY 请求重复消费幂等实验数据准备
--
-- 作用范围固定为：
--   database: course_concurrency
--   user:     stu_newbie
--   courses:  10, 11, 12
--
-- 本脚本会删除上述用户和课程的学习行为与推荐评分快照，并重置学习进度。
-- 只能在实验数据库执行，禁止将 USE 语句改成日常开发库或生产库。

USE course_concurrency;

START TRANSACTION;

SET @con03_user_id := (
    SELECT id
    FROM `user`
    WHERE username = 'stu_newbie'
    LIMIT 1
);

-- 三轮实验必须从空行为日志开始，否则相同 eventId 会被上一轮结果直接判为回放。
DELETE FROM learning_behavior
WHERE user_id = @con03_user_id
  AND course_id IN (10, 11, 12);

-- STUDY 提交后会刷新推荐评分快照；一并清理，避免旧聚合结果干扰观察。
DELETE FROM recommend_user_course_score
WHERE user_id = @con03_user_id
  AND course_id IN (10, 11, 12);

-- 确保三门课程均已选课，并统一从 0 秒、未开始状态起步。
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
    @con03_user_id,
    c.id,
    0,
    0,
    0,
    NULL,
    NULL,
    0,
    0
FROM course c
WHERE @con03_user_id IS NOT NULL
  AND c.id IN (10, 11, 12)
ON DUPLICATE KEY UPDATE
    progress = VALUES(progress),
    learned_seconds = VALUES(learned_seconds),
    status = VALUES(status),
    last_learn_time = VALUES(last_learn_time),
    complete_time = VALUES(complete_time),
    is_favorite = VALUES(is_favorite),
    progress_seconds = VALUES(progress_seconds);

COMMIT;

-- 必须返回 event_id 字段和两列唯一索引，确认 V3 迁移已经应用。
SELECT
    c.column_name,
    c.column_type,
    c.is_nullable,
    c.character_set_name,
    c.collation_name
FROM information_schema.columns c
WHERE c.table_schema = DATABASE()
  AND c.table_name = 'learning_behavior'
  AND c.column_name = 'event_id';

SELECT
    s.index_name,
    s.seq_in_index,
    s.column_name,
    s.non_unique
FROM information_schema.statistics s
WHERE s.table_schema = DATABASE()
  AND s.table_name = 'learning_behavior'
  AND s.index_name = 'uk_learning_behavior_user_event'
ORDER BY s.seq_in_index;

-- 必须返回三行，状态均为 0/0，且视频时长均为 705 秒。
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
WHERE u.username = 'stu_newbie'
  AND ucr.course_id IN (10, 11, 12)
ORDER BY ucr.course_id;

-- 必须返回 behavior_rows = 0。
SELECT COUNT(*) AS behavior_rows
FROM learning_behavior
WHERE user_id = @con03_user_id
  AND course_id IN (10, 11, 12);
