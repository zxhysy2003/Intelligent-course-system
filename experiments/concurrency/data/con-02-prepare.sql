-- CON-02 并发学习进度实验数据准备
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

SET @con02_user_id := (
    SELECT id
    FROM `user`
    WHERE username = 'stu_newbie'
    LIMIT 1
);

-- 每轮实验必须从空行为日志开始，否则无法断言 STUDY/FINISH 的精确数量。
DELETE FROM learning_behavior
WHERE user_id = @con02_user_id
  AND course_id IN (10, 11, 12);

-- 学习行为提交后会刷新推荐评分快照；一并清理，避免上一次实验留下的聚合结果干扰观察。
DELETE FROM recommend_user_course_score
WHERE user_id = @con02_user_id
  AND course_id IN (10, 11, 12);

-- 即使没有先运行 CON-01，也确保三门课程已经处于“已选课”状态。
-- 课程 10 从 0 秒开始，用于验证 100 × 5 秒的并发累加。
-- 课程 11、12 从 700/705 秒开始，分别用于单实例和双实例首次完课。
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
    @con02_user_id,
    c.id,
    CASE WHEN c.id = 10 THEN 0 ELSE 99 END,
    CASE WHEN c.id = 10 THEN 0 ELSE 700 END,
    CASE WHEN c.id = 10 THEN 0 ELSE 1 END,
    NULL,
    NULL,
    0,
    CASE WHEN c.id = 10 THEN 0 ELSE 700 END
FROM course c
WHERE @con02_user_id IS NOT NULL
  AND c.id IN (10, 11, 12)
ON DUPLICATE KEY UPDATE
    progress = VALUES(progress),
    learned_seconds = VALUES(learned_seconds),
    status = VALUES(status),
    last_learn_time = VALUES(last_learn_time),
    complete_time = VALUES(complete_time),
    progress_seconds = VALUES(progress_seconds);

COMMIT;

-- 必须返回三行，且视频时长均为 705 秒。
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
WHERE user_id = @con02_user_id
  AND course_id IN (10, 11, 12);
