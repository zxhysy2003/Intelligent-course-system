-- CON-04 推荐缓存击穿与雪崩实验用户准备
-- 固定作用于 course_concurrency、con04_user_001 ~ con04_user_100 和指标观测账号 con04_admin。
-- 每个用户写入 600 秒 STUDY 信号，确保进入普通推荐分支而不是冷启动分支。

USE course_concurrency;

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS con04_sequence;
CREATE TEMPORARY TABLE con04_sequence (
    n int NOT NULL PRIMARY KEY
);

INSERT INTO con04_sequence (n)
WITH RECURSIVE sequence_numbers AS (
    SELECT 1 AS n
    UNION ALL
    SELECT n + 1
    FROM sequence_numbers
    WHERE n < 100
)
SELECT n FROM sequence_numbers;

INSERT INTO `user` (
    username,
    password,
    nickname,
    email,
    phone,
    role,
    status,
    deleted
)
SELECT
    CONCAT('con04_user_', LPAD(n, 3, '0')),
    '123456',
    CONCAT('CON-04 用户 ', LPAD(n, 3, '0')),
    CONCAT('con04_user_', LPAD(n, 3, '0'), '@example.test'),
    NULL,
    'STUDENT',
    1,
    0
FROM con04_sequence
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    nickname = VALUES(nickname),
    email = VALUES(email),
    role = VALUES(role),
    status = VALUES(status),
    deleted = VALUES(deleted);

-- k6 收尾需读取每个后端实例的 Actuator 构建任务数；管理指标仍保持 ADMIN 权限。
INSERT INTO `user` (
    username,
    password,
    nickname,
    email,
    phone,
    role,
    status,
    deleted
)
VALUES (
    'con04_admin',
    '123456',
    'CON-04 指标观测员',
    'con04_admin@example.test',
    NULL,
    'ADMIN',
    1,
    0
)
ON DUPLICATE KEY UPDATE
    password = VALUES(password),
    nickname = VALUES(nickname),
    email = VALUES(email),
    role = VALUES(role),
    status = VALUES(status),
    deleted = VALUES(deleted);

DROP TEMPORARY TABLE IF EXISTS con04_user_ids;
CREATE TEMPORARY TABLE con04_user_ids AS
SELECT id, username
FROM `user`
WHERE username REGEXP '^con04_user_[0-9]{3}$';

-- 重跑时只清理 CON-04 用户自身数据，不影响其他实验账号。
DELETE lb
FROM learning_behavior lb
JOIN con04_user_ids cu ON cu.id = lb.user_id;

DELETE rucs
FROM recommend_user_course_score rucs
JOIN con04_user_ids cu ON cu.id = rucs.user_id;

DELETE ucr
FROM user_course_relation ucr
JOIN con04_user_ids cu ON cu.id = ucr.user_id;

DELETE uit
FROM user_interest_tag uit
JOIN con04_user_ids cu ON cu.id = uit.user_id;

DELETE uop
FROM user_onboarding_profile uop
JOIN con04_user_ids cu ON cu.id = uop.user_id;

INSERT INTO learning_behavior (
    user_id,
    course_id,
    event_id,
    behavior_type,
    duration,
    create_time
)
SELECT
    cu.id,
    1,
    CONCAT('con04-signal-', RIGHT(cu.username, 3)),
    'STUDY',
    600,
    CURRENT_TIMESTAMP
FROM con04_user_ids cu;

COMMIT;

-- 必须返回 100 个用户，且每个用户都有 600 秒学习信号。
SELECT
    COUNT(*) AS experiment_user_count,
    MIN(id) AS min_user_id,
    MAX(id) AS max_user_id
FROM con04_user_ids;

SELECT
    COUNT(*) AS regular_user_signal_count,
    MIN(user_signals.total_study_seconds) AS min_study_seconds,
    MAX(user_signals.total_study_seconds) AS max_study_seconds
FROM (
    SELECT lb.user_id, SUM(lb.duration) AS total_study_seconds
    FROM learning_behavior lb
    JOIN con04_user_ids cu ON cu.id = lb.user_id
    WHERE lb.behavior_type = 'STUDY'
    GROUP BY lb.user_id
) user_signals;

SELECT id, username
FROM con04_user_ids
ORDER BY username
LIMIT 5;
