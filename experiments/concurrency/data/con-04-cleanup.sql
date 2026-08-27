-- 删除 CON-04 专用用户和它们生成的数据。只允许在 course_concurrency 执行。

USE course_concurrency;

START TRANSACTION;

DROP TEMPORARY TABLE IF EXISTS con04_user_ids;
CREATE TEMPORARY TABLE con04_user_ids AS
SELECT id
FROM `user`
WHERE username REGEXP '^con04_user_[0-9]{3}$';

DELETE lb FROM learning_behavior lb JOIN con04_user_ids cu ON cu.id = lb.user_id;
DELETE rucs FROM recommend_user_course_score rucs JOIN con04_user_ids cu ON cu.id = rucs.user_id;
DELETE ucr FROM user_course_relation ucr JOIN con04_user_ids cu ON cu.id = ucr.user_id;
DELETE uit FROM user_interest_tag uit JOIN con04_user_ids cu ON cu.id = uit.user_id;
DELETE uop FROM user_onboarding_profile uop JOIN con04_user_ids cu ON cu.id = uop.user_id;
DELETE u FROM `user` u JOIN con04_user_ids cu ON cu.id = u.id;

COMMIT;

SELECT COUNT(*) AS remaining_con04_users
FROM `user`
WHERE username REGEXP '^con04_user_[0-9]{3}$';
