ALTER TABLE user_course_relation ADD COLUMN last_view_recorded_at datetime(6) NULL;
UPDATE user_course_relation r JOIN (
    SELECT user_id, course_id, MAX(create_time) AS last_view
    FROM learning_behavior WHERE behavior_type = 'VIEW' GROUP BY user_id, course_id
) v ON v.user_id=r.user_id AND v.course_id=r.course_id
SET r.last_view_recorded_at=v.last_view;

CREATE TABLE learning_outbox_task (
    id varchar(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
    source_event_id varchar(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_type varchar(32) NOT NULL,
    user_id bigint NOT NULL,
    course_id bigint NOT NULL,
    payload json NOT NULL,
    dependency_id varchar(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    mastery_dependency_id varchar(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    attempts int NOT NULL DEFAULT 0,
    next_attempt_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    lease_token varchar(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_until datetime(6) NULL,
    last_error varchar(1000) NULL,
    created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at datetime(6) NULL,
    UNIQUE KEY uk_learning_outbox_source_type (source_event_id, task_type),
    KEY idx_learning_outbox_pending (status, next_attempt_at),
    KEY idx_learning_outbox_lease (status, lease_until)
) ENGINE=InnoDB;
