ALTER TABLE `agent_message`
  ADD COLUMN `client_message_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '客户端发送幂等ID' AFTER `user_id`;

CREATE UNIQUE INDEX `uk_agent_message_user_role_client`
  ON `agent_message` (`user_id`, `role`, `client_message_id`);
