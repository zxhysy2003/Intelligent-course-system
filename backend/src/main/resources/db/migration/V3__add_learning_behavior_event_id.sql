ALTER TABLE `learning_behavior`
  ADD COLUMN `event_id` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL
    COMMENT '客户端学习事件幂等ID，仅STUDY必填' AFTER `course_id`,
  ADD UNIQUE INDEX `uk_learning_behavior_user_event` (`user_id`, `event_id`);
