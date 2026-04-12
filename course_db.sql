/*
 Navicat Premium Dump SQL

 Source Server         : macos_course_db
 Source Server Type    : MySQL
 Source Server Version : 80044 (8.0.44)
 Source Host           : 192.168.3.94:3306
 Source Schema         : course_db

 Target Server Type    : MySQL
 Target Server Version : 80044 (8.0.44)
 File Encoding         : 65001

 Date: 12/04/2026 11:55:59
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for behavior_weight
-- ----------------------------
DROP TABLE IF EXISTS `behavior_weight`;
CREATE TABLE `behavior_weight`  (
  `behavior_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '行为类型',
  `weight` double NOT NULL COMMENT '权重值',
  PRIMARY KEY (`behavior_type`) USING BTREE,
  INDEX `idx_behavior_type`(`behavior_type` ASC) USING BTREE,
  CONSTRAINT `chk_behavior_weight_positive` CHECK (`weight` >= 0)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '行为权重配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of behavior_weight
-- ----------------------------
INSERT INTO `behavior_weight` VALUES ('FAVORITE', 4);
INSERT INTO `behavior_weight` VALUES ('FINISH', 5);
INSERT INTO `behavior_weight` VALUES ('STUDY', 3);
INSERT INTO `behavior_weight` VALUES ('VIEW', 1);

-- ----------------------------
-- Table structure for category
-- ----------------------------
DROP TABLE IF EXISTS `category`;
CREATE TABLE `category`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类别名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '类别描述',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of category
-- ----------------------------
INSERT INTO `category` VALUES (1, '前端', NULL, '2026-01-18 14:56:48', '2026-01-18 14:56:48');
INSERT INTO `category` VALUES (2, '数据', NULL, '2026-01-18 14:57:02', '2026-01-18 14:57:02');
INSERT INTO `category` VALUES (3, '计算机基础', NULL, '2026-01-18 14:57:10', '2026-01-18 14:57:10');
INSERT INTO `category` VALUES (4, '后端', NULL, '2026-01-18 14:57:20', '2026-01-18 14:57:20');

-- ----------------------------
-- Table structure for course
-- ----------------------------
DROP TABLE IF EXISTS `course`;
CREATE TABLE `course`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '课程ID',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程标题',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '课程简介',
  `cover_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '课程封面URL',
  `difficulty` tinyint NULL DEFAULT NULL COMMENT '难度：1-初级 2-中级 3-高级',
  `duration` int NULL DEFAULT NULL COMMENT '课程总学时（秒数）',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态：0-草稿 1-上线 2-下线',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_course_status`(`status` ASC) USING BTREE,
  INDEX `idx_course_difficulty`(`difficulty` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of course
-- ----------------------------
INSERT INTO `course` VALUES (1, 'Java 基础入门', '从零开始学习 Java 语法与面向对象思想', 'https://images.unsplash.com/photo-1518770660439-4636190af475', 1, 705, 1, '2026-01-02 14:15:15', '2026-01-31 15:13:16');
INSERT INTO `course` VALUES (2, 'Java 面向对象进阶', '深入理解 Java 面向对象设计思想与实践', 'https://images.unsplash.com/photo-1504639725590-34d0984388bd', 2, 705, 1, '2026-01-02 14:15:15', '2026-01-31 15:13:16');
INSERT INTO `course` VALUES (3, 'Spring Boot 从入门到实战', '基于 Spring Boot 构建企业级后端应用', 'https://images.unsplash.com/photo-1555949963-aa79dcee981c', 2, 705, 1, '2026-01-02 14:15:15', '2026-01-31 15:13:16');
INSERT INTO `course` VALUES (4, 'MySQL 数据库基础', '学习关系型数据库的基本原理与 SQL 编程', 'https://images.unsplash.com/photo-1544383835-bda2bc66a55d', 1, 705, 1, '2026-01-02 14:15:15', '2026-01-31 15:13:16');
INSERT INTO `course` VALUES (5, 'MySQL 性能优化实战', '掌握索引、执行计划与 SQL 调优技巧', 'https://images.unsplash.com/photo-1558494949-ef010cbdcc31', 3, 705, 1, '2026-01-02 14:15:15', '2026-01-31 15:13:16');
INSERT INTO `course` VALUES (6, 'Vue.js 前端开发基础', '使用 Vue.js 构建现代前端应用', 'https://images.unsplash.com/photo-1555066931-4365d14bab8c', 1, 705, 1, '2026-01-02 14:15:15', '2026-01-31 15:13:16');
INSERT INTO `course` VALUES (7, 'Vue + Spring Boot 前后端分离实战', '实现完整的前后端分离项目', 'https://miro.medium.com/v2/resize:fit:1100/format:webp/0*ZHRCr9IY5RNmcJi-.png', 2, 705, 1, '2026-01-02 14:15:15', '2026-04-02 22:00:36');
INSERT INTO `course` VALUES (8, 'Python 数据分析入门', '利用 Python 进行数据分析与可视化', 'https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5', 1, 705, 1, '2026-01-02 14:15:15', '2026-01-31 15:13:16');
INSERT INTO `course` VALUES (9, '推荐系统原理与实战', '协同过滤与内容推荐算法详解', 'https://images.unsplash.com/photo-1534759846116-5799c33ce22a', 3, 705, 1, '2026-01-02 14:15:15', '2026-01-31 15:13:16');
INSERT INTO `course` VALUES (10, 'Redis 核心原理与应用', '深入理解 Redis 数据结构与缓存设计', 'https://images.unsplash.com/photo-1544197150-b99a580bb7a8', 2, 705, 1, '2026-01-02 14:15:15', '2026-01-31 15:13:16');
INSERT INTO `course` VALUES (11, '操作系统原理', '计算机操作系统的基本概念与实现机制', 'https://images.unsplash.com/photo-1517433456452-f9633a875f6f', 2, 705, 1, '2026-01-02 14:15:15', '2026-01-31 15:13:16');
INSERT INTO `course` VALUES (12, '计算机网络基础', '深入理解 TCP/IP 协议与网络通信', 'https://images.unsplash.com/photo-1504384308090-c894fdcc538d', 1, 705, 1, '2026-01-02 14:15:15', '2026-01-31 15:13:16');
INSERT INTO `course` VALUES (14, 'test2', '测试使用。。。', 'https://www.economist.com/cdn-cgi/image/width=1424,quality=80,format=auto/content-assets/images/20260228_LDP001.jpg', 1, 705, 1, '2026-02-27 14:45:07', '2026-02-27 14:47:48');

-- ----------------------------
-- Table structure for course_category_relation
-- ----------------------------
DROP TABLE IF EXISTS `course_category_relation`;
CREATE TABLE `course_category_relation`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL COMMENT '课程ID',
  `category_id` bigint NOT NULL COMMENT '类别ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_course_category`(`course_id` ASC, `category_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 16 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of course_category_relation
-- ----------------------------
INSERT INTO `course_category_relation` VALUES (1, 1, 4, '2026-01-18 15:07:17');
INSERT INTO `course_category_relation` VALUES (2, 2, 4, '2026-01-18 15:07:34');
INSERT INTO `course_category_relation` VALUES (3, 3, 4, '2026-01-18 15:07:42');
INSERT INTO `course_category_relation` VALUES (4, 4, 4, '2026-01-18 15:07:50');
INSERT INTO `course_category_relation` VALUES (5, 5, 4, '2026-01-18 15:08:02');
INSERT INTO `course_category_relation` VALUES (6, 6, 1, '2026-01-18 15:08:09');
INSERT INTO `course_category_relation` VALUES (7, 7, 4, '2026-01-18 15:08:26');
INSERT INTO `course_category_relation` VALUES (8, 8, 2, '2026-01-18 15:08:34');
INSERT INTO `course_category_relation` VALUES (9, 9, 4, '2026-01-18 15:08:43');
INSERT INTO `course_category_relation` VALUES (10, 10, 4, '2026-01-18 15:08:53');
INSERT INTO `course_category_relation` VALUES (11, 11, 3, '2026-01-18 15:09:03');
INSERT INTO `course_category_relation` VALUES (12, 12, 3, '2026-01-18 15:09:09');
INSERT INTO `course_category_relation` VALUES (14, 14, 3, '2026-02-27 14:45:07');

-- ----------------------------
-- Table structure for course_knowledge_point
-- ----------------------------
DROP TABLE IF EXISTS `course_knowledge_point`;
CREATE TABLE `course_knowledge_point`  (
  `course_id` bigint NOT NULL COMMENT '课程ID',
  `kp_id` bigint NOT NULL COMMENT '知识点ID',
  PRIMARY KEY (`course_id`, `kp_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程-知识点关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of course_knowledge_point
-- ----------------------------
INSERT INTO `course_knowledge_point` VALUES (1, 1);
INSERT INTO `course_knowledge_point` VALUES (1, 2);
INSERT INTO `course_knowledge_point` VALUES (1, 3);
INSERT INTO `course_knowledge_point` VALUES (1, 4);
INSERT INTO `course_knowledge_point` VALUES (2, 2);
INSERT INTO `course_knowledge_point` VALUES (2, 3);
INSERT INTO `course_knowledge_point` VALUES (2, 5);
INSERT INTO `course_knowledge_point` VALUES (3, 6);
INSERT INTO `course_knowledge_point` VALUES (3, 7);
INSERT INTO `course_knowledge_point` VALUES (3, 8);
INSERT INTO `course_knowledge_point` VALUES (3, 9);
INSERT INTO `course_knowledge_point` VALUES (4, 10);
INSERT INTO `course_knowledge_point` VALUES (5, 11);
INSERT INTO `course_knowledge_point` VALUES (5, 12);
INSERT INTO `course_knowledge_point` VALUES (6, 15);
INSERT INTO `course_knowledge_point` VALUES (7, 8);
INSERT INTO `course_knowledge_point` VALUES (7, 9);
INSERT INTO `course_knowledge_point` VALUES (7, 15);
INSERT INTO `course_knowledge_point` VALUES (7, 16);
INSERT INTO `course_knowledge_point` VALUES (8, 17);
INSERT INTO `course_knowledge_point` VALUES (8, 18);
INSERT INTO `course_knowledge_point` VALUES (8, 19);
INSERT INTO `course_knowledge_point` VALUES (8, 20);
INSERT INTO `course_knowledge_point` VALUES (9, 21);
INSERT INTO `course_knowledge_point` VALUES (9, 22);
INSERT INTO `course_knowledge_point` VALUES (10, 13);
INSERT INTO `course_knowledge_point` VALUES (10, 14);
INSERT INTO `course_knowledge_point` VALUES (11, 23);
INSERT INTO `course_knowledge_point` VALUES (12, 24);
INSERT INTO `course_knowledge_point` VALUES (14, 1);

-- ----------------------------
-- Table structure for course_tag
-- ----------------------------
DROP TABLE IF EXISTS `course_tag`;
CREATE TABLE `course_tag`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_id` bigint NOT NULL COMMENT '课程ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `tag_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_course_tag`(`course_id` ASC, `tag_id` ASC) USING BTREE,
  INDEX `idx_course_id`(`course_id` ASC) USING BTREE,
  INDEX `idx_tag_id`(`tag_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 43 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程-标签关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of course_tag
-- ----------------------------
INSERT INTO `course_tag` VALUES (1, 1, 1, '2026-01-18 15:17:47', 'Java');
INSERT INTO `course_tag` VALUES (2, 1, 7, '2026-01-18 15:17:47', '后端开发');
INSERT INTO `course_tag` VALUES (3, 1, 11, '2026-01-18 15:17:47', '入门');
INSERT INTO `course_tag` VALUES (4, 2, 1, '2026-01-18 15:17:47', 'Java');
INSERT INTO `course_tag` VALUES (5, 2, 7, '2026-01-18 15:17:47', '后端开发');
INSERT INTO `course_tag` VALUES (6, 2, 12, '2026-01-18 15:17:47', '进阶');
INSERT INTO `course_tag` VALUES (7, 3, 1, '2026-01-18 15:18:49', 'Java');
INSERT INTO `course_tag` VALUES (8, 3, 2, '2026-01-18 15:18:54', 'Spring Boot');
INSERT INTO `course_tag` VALUES (9, 3, 7, '2026-01-18 15:19:00', '后端开发');
INSERT INTO `course_tag` VALUES (10, 3, 13, '2026-01-18 15:19:06', '实战');
INSERT INTO `course_tag` VALUES (11, 4, 3, '2026-01-18 15:19:21', 'MySQL');
INSERT INTO `course_tag` VALUES (12, 4, 7, '2026-01-18 15:19:21', '后端开发');
INSERT INTO `course_tag` VALUES (13, 4, 11, '2026-01-18 15:19:21', '入门');
INSERT INTO `course_tag` VALUES (14, 5, 3, '2026-01-18 15:19:21', 'MySQL');
INSERT INTO `course_tag` VALUES (15, 5, 7, '2026-01-18 15:19:21', '后端开发');
INSERT INTO `course_tag` VALUES (16, 5, 13, '2026-01-18 15:19:21', '实战');
INSERT INTO `course_tag` VALUES (17, 6, 4, '2026-01-18 15:19:41', 'Vue');
INSERT INTO `course_tag` VALUES (18, 6, 8, '2026-01-18 15:19:41', '前端开发');
INSERT INTO `course_tag` VALUES (19, 6, 11, '2026-01-18 15:19:41', '入门');
INSERT INTO `course_tag` VALUES (20, 7, 4, '2026-01-18 15:19:42', 'Vue');
INSERT INTO `course_tag` VALUES (21, 7, 2, '2026-01-18 15:19:42', 'Spring Boot');
INSERT INTO `course_tag` VALUES (22, 7, 7, '2026-01-18 15:19:42', '后端开发');
INSERT INTO `course_tag` VALUES (23, 7, 8, '2026-01-18 15:19:42', '前端开发');
INSERT INTO `course_tag` VALUES (24, 7, 13, '2026-01-18 15:19:42', '实战');
INSERT INTO `course_tag` VALUES (25, 8, 5, '2026-01-18 15:19:57', 'Python');
INSERT INTO `course_tag` VALUES (26, 8, 9, '2026-01-18 15:19:57', '数据分析');
INSERT INTO `course_tag` VALUES (27, 8, 11, '2026-01-18 15:19:57', '入门');
INSERT INTO `course_tag` VALUES (28, 9, 10, '2026-01-18 15:19:57', '推荐系统');
INSERT INTO `course_tag` VALUES (29, 9, 9, '2026-01-18 15:19:57', '数据分析');
INSERT INTO `course_tag` VALUES (30, 9, 13, '2026-01-18 15:19:57', '实战');
INSERT INTO `course_tag` VALUES (31, 10, 6, '2026-01-18 15:20:10', 'Redis');
INSERT INTO `course_tag` VALUES (32, 10, 7, '2026-01-18 15:20:10', '后端开发');
INSERT INTO `course_tag` VALUES (33, 10, 13, '2026-01-18 15:20:10', '实战');
INSERT INTO `course_tag` VALUES (34, 11, 14, '2026-01-18 15:20:25', '计算机基础');
INSERT INTO `course_tag` VALUES (35, 12, 14, '2026-01-18 15:20:25', '计算机基础');
INSERT INTO `course_tag` VALUES (39, 14, 11, '2026-02-27 14:45:07', '入门');

-- ----------------------------
-- Table structure for knowledge_dimension
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_dimension`;
CREATE TABLE `knowledge_dimension`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '维度ID',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维度编码',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维度名称',
  `sort` int NULL DEFAULT 0 COMMENT '排序',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态：1启用 0禁用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_code`(`code` ASC) USING BTREE,
  UNIQUE INDEX `uk_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 11 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识点维度表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of knowledge_dimension
-- ----------------------------
INSERT INTO `knowledge_dimension` VALUES (1, 'JAVA', 'Java', 10, 1, '2026-02-15 20:21:10', '2026-02-15 20:21:10');
INSERT INTO `knowledge_dimension` VALUES (2, 'WEB_BACKEND', 'Web后端', 20, 1, '2026-02-15 20:21:10', '2026-02-15 20:21:10');
INSERT INTO `knowledge_dimension` VALUES (3, 'SPRING', 'Spring', 30, 1, '2026-02-15 20:21:10', '2026-02-15 20:21:10');
INSERT INTO `knowledge_dimension` VALUES (4, 'DB', '数据库', 40, 1, '2026-02-15 20:21:10', '2026-02-15 20:21:10');
INSERT INTO `knowledge_dimension` VALUES (5, 'REDIS', 'Redis', 50, 1, '2026-02-15 20:21:10', '2026-02-15 20:21:10');
INSERT INTO `knowledge_dimension` VALUES (6, 'FRONTEND', '前端', 60, 1, '2026-02-15 20:21:10', '2026-02-15 20:21:10');
INSERT INTO `knowledge_dimension` VALUES (7, 'PY_DATA', 'Python数据分析', 70, 1, '2026-02-15 20:21:10', '2026-02-15 20:21:10');
INSERT INTO `knowledge_dimension` VALUES (8, 'RECSYS', '推荐系统', 80, 1, '2026-02-15 20:21:10', '2026-02-15 20:21:10');
INSERT INTO `knowledge_dimension` VALUES (9, 'CS_FOUNDATION', '计算机基础', 90, 1, '2026-02-15 20:21:10', '2026-02-15 20:21:10');
INSERT INTO `knowledge_dimension` VALUES (10, 'UNCATEGORIZED', '未分类', 999, 1, '2026-02-15 20:21:10', '2026-02-15 20:21:10');

-- ----------------------------
-- Table structure for knowledge_point
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_point`;
CREATE TABLE `knowledge_point`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识点ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识点名称',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '知识点描述',
  `difficulty` tinyint NULL DEFAULT NULL COMMENT '难度等级：1-入门 2-基础 3-进阶 4-高级',
  `dimension_id` bigint NOT NULL COMMENT '知识点维度ID',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态：1-启用 0-禁用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_name`(`name` ASC) USING BTREE,
  INDEX `idx_dimension_id`(`dimension_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 26 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识点表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of knowledge_point
-- ----------------------------
INSERT INTO `knowledge_point` VALUES (1, 'Java 基础语法', '变量、流程控制、数组等基础语法', 1, 1, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (2, '面向对象思想', '封装、继承、多态', 2, 1, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (3, '集合框架', 'List、Set、Map 等集合容器', 2, 1, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (4, '异常处理', '异常体系与处理机制', 2, 1, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (5, 'JVM 原理', '内存模型、垃圾回收机制', 3, 1, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (6, 'HTTP 协议', '请求响应模型与状态码', 1, 2, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (7, 'Spring Core', 'IOC 与 AOP 核心机制', 2, 3, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (8, 'Spring MVC', 'Web MVC 架构与控制器', 2, 3, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (9, 'Spring Boot 核心', '自动配置与 Starter 机制', 2, 3, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (10, 'SQL 基础', 'DDL/DML/DQL 语句', 1, 4, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (11, '索引原理', 'B+ 树与索引设计', 3, 4, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (12, '执行计划分析', 'Explain 使用与优化', 3, 4, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (13, 'Redis 数据结构', 'String/Hash/List/Set/ZSet', 2, 5, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (14, '缓存一致性', '缓存更新策略', 3, 5, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (15, 'Vue 基础语法', '指令、组件、响应式', 1, 6, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (16, '前端工程化', 'Vite、模块化、构建工具', 2, 6, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (17, 'Python 基础语法', '变量、函数、控制流', 1, 7, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (18, 'NumPy 基础', '数组运算', 2, 7, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (19, 'Pandas 数据处理', '数据清洗与分析', 2, 7, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (20, '数据可视化', 'Matplotlib 使用', 2, 7, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (21, '协同过滤算法', 'UserCF 与 ItemCF', 3, 8, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (22, '内容推荐算法', 'TF-IDF 与向量化', 3, 8, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (23, '操作系统基础', '进程线程与内存管理', 2, 9, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (24, 'TCP/IP 协议', '传输层协议原理', 2, 9, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');
INSERT INTO `knowledge_point` VALUES (25, '分布式基础', 'CAP 理论与一致性', 3, 9, 1, '2026-02-01 17:27:37', '2026-02-15 20:23:55');

-- ----------------------------
-- Table structure for learning_behavior
-- ----------------------------
DROP TABLE IF EXISTS `learning_behavior`;
CREATE TABLE `learning_behavior`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `course_id` bigint NOT NULL COMMENT '课程ID',
  `behavior_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '行为类型',
  `duration` int NULL DEFAULT 0 COMMENT '学习时长（秒）',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '行为发生时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_course`(`user_id` ASC, `course_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2017481972795961377 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '学习行为日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of learning_behavior
-- ----------------------------
INSERT INTO `learning_behavior` VALUES (2017481972795961347, 2, 1, 'VIEW', 0, '2026-03-20 20:00:10');
INSERT INTO `learning_behavior` VALUES (2017481972795961348, 2, 1, 'STUDY', 180, '2026-03-20 20:10:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961349, 2, 1, 'STUDY', 240, '2026-03-28 21:00:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961350, 2, 1, 'STUDY', 180, '2026-04-03 21:10:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961351, 2, 1, 'FAVORITE', 0, '2026-03-20 20:05:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961352, 2, 3, 'VIEW', 0, '2026-03-25 19:30:10');
INSERT INTO `learning_behavior` VALUES (2017481972795961353, 2, 3, 'STUDY', 120, '2026-03-25 19:40:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961354, 2, 3, 'STUDY', 200, '2026-04-02 22:15:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961355, 2, 4, 'VIEW', 0, '2026-03-30 22:40:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961356, 2, 4, 'STUDY', 110, '2026-03-30 23:00:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961357, 3, 6, 'VIEW', 0, '2026-04-04 09:00:10');
INSERT INTO `learning_behavior` VALUES (2017481972795961358, 3, 6, 'STUDY', 70, '2026-04-04 09:20:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961359, 3, 6, 'FAVORITE', 0, '2026-04-04 09:05:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961360, 3, 7, 'FAVORITE', 0, '2026-04-03 12:00:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961361, 3, 1, 'VIEW', 0, '2026-04-01 18:00:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961362, 3, 1, 'STUDY', 30, '2026-04-01 18:05:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961363, 4, 8, 'VIEW', 0, '2026-03-20 09:00:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961364, 4, 8, 'STUDY', 300, '2026-03-20 09:30:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961365, 4, 8, 'STUDY', 405, '2026-03-28 21:35:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961366, 4, 8, 'FINISH', 0, '2026-03-28 21:40:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961367, 4, 8, 'FAVORITE', 0, '2026-03-20 09:05:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961368, 4, 9, 'VIEW', 0, '2026-03-23 10:00:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961369, 4, 9, 'STUDY', 705, '2026-03-31 20:05:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961370, 4, 9, 'FINISH', 0, '2026-03-31 20:10:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961371, 4, 5, 'VIEW', 0, '2026-04-01 08:00:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961372, 4, 5, 'STUDY', 200, '2026-04-01 08:40:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961373, 4, 5, 'STUDY', 220, '2026-04-02 08:30:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961374, 5, 11, 'VIEW', 0, '2026-04-03 13:50:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961375, 5, 11, 'STUDY', 15, '2026-04-03 14:00:00');
INSERT INTO `learning_behavior` VALUES (2017481972795961376, 5, 12, 'VIEW', 0, '2026-04-02 16:00:00');

-- ----------------------------
-- Table structure for tag
-- ----------------------------
DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
  `type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标签类型（方向/技术/难度等）',
  `status` tinyint NULL DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tag_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '课程标签表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of tag
-- ----------------------------
INSERT INTO `tag` VALUES (1, 'Java', 'TECH', 1, '2026-01-18 15:16:53', '2026-01-18 15:16:53');
INSERT INTO `tag` VALUES (2, 'Spring Boot', 'TECH', 1, '2026-01-18 15:16:53', '2026-01-18 15:16:53');
INSERT INTO `tag` VALUES (3, 'MySQL', 'TECH', 1, '2026-01-18 15:16:53', '2026-01-18 15:16:53');
INSERT INTO `tag` VALUES (4, 'Vue', 'TECH', 1, '2026-01-18 15:16:53', '2026-01-18 15:16:53');
INSERT INTO `tag` VALUES (5, 'Python', 'TECH', 1, '2026-01-18 15:16:53', '2026-01-18 15:16:53');
INSERT INTO `tag` VALUES (6, 'Redis', 'TECH', 1, '2026-01-18 15:16:53', '2026-01-18 15:16:53');
INSERT INTO `tag` VALUES (7, '后端开发', 'FIELD', 1, '2026-01-18 15:16:53', '2026-01-18 15:16:53');
INSERT INTO `tag` VALUES (8, '前端开发', 'FIELD', 1, '2026-01-18 15:16:53', '2026-01-18 15:16:53');
INSERT INTO `tag` VALUES (9, '数据分析', 'FIELD', 1, '2026-01-18 15:16:53', '2026-01-18 15:16:53');
INSERT INTO `tag` VALUES (10, '推荐系统', 'FIELD', 1, '2026-01-18 15:16:53', '2026-01-18 15:16:53');
INSERT INTO `tag` VALUES (11, '入门', 'LEVEL', 1, '2026-01-18 15:16:53', '2026-01-18 15:16:53');
INSERT INTO `tag` VALUES (12, '进阶', 'LEVEL', 1, '2026-01-18 15:16:53', '2026-01-18 15:16:53');
INSERT INTO `tag` VALUES (13, '实战', 'LEVEL', 1, '2026-01-18 15:16:53', '2026-01-18 15:16:53');
INSERT INTO `tag` VALUES (14, '计算机基础', 'THEORY', 1, '2026-01-18 15:16:53', '2026-01-18 15:16:53');

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录账号',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '加密密码',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '昵称',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '手机号',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STUDENT' COMMENT '角色：STUDENT / TEACHER / ADMIN',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1-正常 0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '系统用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user
-- ----------------------------
INSERT INTO `user` VALUES (1, 'admin', '123456', 'sy', '123456@shou.come', '18621503807', 'ADMIN', 1, '2025-12-28 20:06:20', '2026-02-04 22:26:45', 0);
INSERT INTO `user` VALUES (2, 'stu_zhangsan', '123456', '张三', 'zhangsan@test.com', '01000000002', 'STUDENT', 1, '2026-01-10 10:00:00', '2026-01-10 10:00:00', 0);
INSERT INTO `user` VALUES (3, 'stu_lisi', '123456', '李四', 'lisi@test.com', '01000000003', 'STUDENT', 1, '2026-01-11 10:00:00', '2026-01-11 10:00:00', 0);
INSERT INTO `user` VALUES (4, 'stu_wangwu', '123456', '王五', 'wangwu@test.com', '01000000004', 'STUDENT', 1, '2026-01-12 10:00:00', '2026-01-12 10:00:00', 0);
INSERT INTO `user` VALUES (5, 'stu_zhaoliu', '123456', '赵六', 'zhaoliu@test.com', '01000000005', 'STUDENT', 1, '2026-01-13 10:00:00', '2026-01-13 10:00:00', 0);
INSERT INTO `user` VALUES (6, 'stu_newbie', '123456', '新同学', 'newbie@test.com', '01000000006', 'STUDENT', 1, '2026-02-01 09:00:00', '2026-02-01 09:00:00', 0);

-- ----------------------------
-- Table structure for user_course_relation
-- ----------------------------
DROP TABLE IF EXISTS `user_course_relation`;
CREATE TABLE `user_course_relation`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `course_id` bigint NOT NULL COMMENT '课程ID',
  `progress` tinyint NULL DEFAULT 0 COMMENT '学习进度(0-100)',
  `learned_seconds` int NULL DEFAULT 0 COMMENT '已学习时长(秒)',
  `status` tinyint NULL DEFAULT 0 COMMENT '学习状态：0-未开始，1-学习中，2-已完成',
  `last_learn_time` datetime NULL DEFAULT NULL COMMENT '最近一次学习时间',
  `complete_time` datetime NULL DEFAULT NULL COMMENT '完成时间',
  `is_favorite` tinyint NULL DEFAULT 0 COMMENT '是否收藏：0-否，1-是',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次学习时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `progress_seconds` int NOT NULL DEFAULT 0 COMMENT '当前观看到第几秒',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_course`(`user_id` ASC, `course_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_course_id`(`course_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2020755789312118787 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户-课程学习关系表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user_course_relation
-- ----------------------------
INSERT INTO `user_course_relation` VALUES (2017589199745622018, 2, 1, 85, 600, 1, '2026-04-03 21:10:00', NULL, 1, '2026-03-20 20:00:00', '2026-04-11 15:30:36', 600);
INSERT INTO `user_course_relation` VALUES (2017589199745622019, 2, 3, 45, 320, 1, '2026-04-02 22:15:00', NULL, 0, '2026-03-25 19:30:00', '2026-04-11 15:30:40', 320);
INSERT INTO `user_course_relation` VALUES (2017589199745622020, 2, 4, 15, 110, 1, '2026-03-30 23:00:00', NULL, 0, '2026-03-30 22:40:00', '2026-04-11 15:30:45', 110);
INSERT INTO `user_course_relation` VALUES (2017589199745622021, 3, 6, 10, 70, 1, '2026-04-04 09:20:00', NULL, 1, '2026-04-04 09:00:00', '2026-04-11 15:30:48', 70);
INSERT INTO `user_course_relation` VALUES (2017589199745622022, 3, 7, 0, 0, 0, NULL, NULL, 1, '2026-04-03 12:00:00', '2026-04-11 15:30:51', 0);
INSERT INTO `user_course_relation` VALUES (2017589199745622023, 3, 1, 5, 30, 1, '2026-04-01 18:05:00', NULL, 0, '2026-04-01 18:00:00', '2026-04-11 15:30:53', 30);
INSERT INTO `user_course_relation` VALUES (2017589199745622024, 4, 8, 100, 705, 2, '2026-03-28 21:40:00', '2026-03-28 21:40:00', 1, '2026-03-20 09:00:00', '2026-04-11 15:30:56', 705);
INSERT INTO `user_course_relation` VALUES (2017589199745622025, 4, 9, 100, 705, 2, '2026-03-31 20:10:00', '2026-03-31 20:10:00', 0, '2026-03-23 10:00:00', '2026-04-11 15:31:00', 705);
INSERT INTO `user_course_relation` VALUES (2017589199745622026, 4, 5, 60, 420, 1, '2026-04-02 08:30:00', NULL, 0, '2026-04-01 08:00:00', '2026-04-11 15:31:02', 420);
INSERT INTO `user_course_relation` VALUES (2017589199745622027, 5, 11, 2, 15, 1, '2026-04-03 14:00:00', NULL, 0, '2026-04-03 13:50:00', '2026-04-11 15:31:04', 15);
INSERT INTO `user_course_relation` VALUES (2017589199745622028, 5, 12, 0, 0, 0, NULL, NULL, 0, '2026-04-02 16:00:00', '2026-04-11 15:31:11', 0);

-- ----------------------------
-- Table structure for user_interest_tag
-- ----------------------------
DROP TABLE IF EXISTS `user_interest_tag`;
CREATE TABLE `user_interest_tag`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID，关联tag表',
  `weight` double NOT NULL DEFAULT 1 COMMENT '兴趣权重',
  `source` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INIT' COMMENT '来源：INIT-初始化选择 BEHAVIOR-行为推断',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_tag`(`user_id` ASC, `tag_id` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_tag_id`(`tag_id` ASC) USING BTREE,
  INDEX `idx_source`(`source` ASC) USING BTREE,
  CONSTRAINT `chk_interest_tag_weight_positive` CHECK (`weight` >= 0)
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户兴趣标签表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of user_interest_tag
-- ----------------------------

-- ----------------------------
-- Table structure for user_onboarding_profile
-- ----------------------------
DROP TABLE IF EXISTS `user_onboarding_profile`;
CREATE TABLE `user_onboarding_profile`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `current_level` tinyint NOT NULL COMMENT '当前基础：1-零基础 2-入门 3-有基础',
  `learning_goal` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '学习目标：JOB/PROJECT/FOUNDATION/EXAM',
  `onboarding_status` tinyint NOT NULL DEFAULT 1 COMMENT '是否完成初始化：0-未完成 1-已完成',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_current_level`(`current_level` ASC) USING BTREE,
  INDEX `idx_learning_goal`(`learning_goal` ASC) USING BTREE,
  CONSTRAINT `chk_current_level` CHECK (`current_level` in (1,2,3)),
  CONSTRAINT `chk_onboarding_status` CHECK (`onboarding_status` in (0,1))
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户冷启动初始化画像表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of user_onboarding_profile
-- ----------------------------

-- ----------------------------
-- Table structure for video
-- ----------------------------
DROP TABLE IF EXISTS `video`;
CREATE TABLE `video`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '视频ID',
  `course_id` bigint NOT NULL COMMENT '所属课程ID',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '视频标题（如：第一章 - 入门介绍）',
  `video_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '视频文件路径（相对于 static 目录，如：video/java_01.mp4）',
  `duration_seconds` int NULL DEFAULT 0 COMMENT '视频时长（秒），可用于前端显示 00:00）',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_course_id`(`course_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 15 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '视频表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of video
-- ----------------------------
INSERT INTO `video` VALUES (1, 12, '计算机网络基础', '1', 705, '2026-01-28 15:48:03', '2026-02-25 23:39:33');
INSERT INTO `video` VALUES (2, 1, 'Java 基础入门', '1', 705, '2026-01-31 14:56:20', '2026-02-25 23:39:33');
INSERT INTO `video` VALUES (3, 2, 'Java 面向对象进阶', '1', 705, '2026-01-31 14:56:20', '2026-02-25 23:39:33');
INSERT INTO `video` VALUES (4, 3, 'Spring Boot 从入门到实战', '1', 705, '2026-01-31 14:56:20', '2026-02-25 23:39:33');
INSERT INTO `video` VALUES (5, 4, 'MySQL 数据库基础', '1', 705, '2026-01-31 14:56:20', '2026-02-25 23:39:33');
INSERT INTO `video` VALUES (6, 5, 'MySQL 性能优化实战', '1', 705, '2026-01-31 14:56:20', '2026-02-25 23:39:33');
INSERT INTO `video` VALUES (7, 6, 'Vue.js 前端开发基础', '1', 705, '2026-01-31 14:56:20', '2026-02-25 23:39:33');
INSERT INTO `video` VALUES (8, 7, 'Vue + Spring Boot 前后端分离实战', '1', 705, '2026-01-31 14:56:20', '2026-02-25 23:39:33');
INSERT INTO `video` VALUES (9, 8, 'Python 数据分析入门', '1', 705, '2026-01-31 14:56:20', '2026-02-25 23:39:33');
INSERT INTO `video` VALUES (10, 9, '推荐系统原理与实战', '1', 705, '2026-01-31 14:56:20', '2026-02-25 23:39:33');
INSERT INTO `video` VALUES (11, 10, 'Redis 核心原理与应用', '1', 705, '2026-01-31 14:56:20', '2026-02-25 23:39:33');
INSERT INTO `video` VALUES (12, 11, '操作系统原理', '1', 705, '2026-01-31 14:56:20', '2026-02-25 23:39:33');
INSERT INTO `video` VALUES (13, 13, 'test', '13/9eb94ce94611453491f597fbd4590e27.mp4', 705, '2026-02-25 23:24:47', '2026-02-27 14:45:54');
INSERT INTO `video` VALUES (14, 14, 'test2', '14/6bf74f400b25470a99820f7d410acde4.mp4', 705, '2026-02-27 14:45:07', '2026-02-27 14:45:07');

SET FOREIGN_KEY_CHECKS = 1;
