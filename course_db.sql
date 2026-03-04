-- MySQL dump 10.13  Distrib 9.6.0, for macos26.2 (arm64)
--
-- Host: 127.0.0.1    Database: course_db
-- ------------------------------------------------------
-- Server version	8.0.44

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `behavior_weight`
--

DROP TABLE IF EXISTS `behavior_weight`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `behavior_weight` (
  `behavior_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '行为类型',
  `weight` double NOT NULL COMMENT '权重值',
  PRIMARY KEY (`behavior_type`),
  KEY `idx_behavior_type` (`behavior_type`),
  CONSTRAINT `chk_weight_positive` CHECK ((`weight` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行为权重配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `behavior_weight`
--

/*!40000 ALTER TABLE `behavior_weight` DISABLE KEYS */;
INSERT INTO `behavior_weight` VALUES ('FAVORITE',4),('FINISH',5),('STUDY',3),('VIEW',1);
/*!40000 ALTER TABLE `behavior_weight` ENABLE KEYS */;

--
-- Table structure for table `category`
--

DROP TABLE IF EXISTS `category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类别名称',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '类别描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `category`
--

/*!40000 ALTER TABLE `category` DISABLE KEYS */;
INSERT INTO `category` VALUES (1,'前端',NULL,'2026-01-18 14:56:48','2026-01-18 14:56:48'),(2,'数据',NULL,'2026-01-18 14:57:02','2026-01-18 14:57:02'),(3,'计算机基础',NULL,'2026-01-18 14:57:10','2026-01-18 14:57:10'),(4,'后端',NULL,'2026-01-18 14:57:20','2026-01-18 14:57:20');
/*!40000 ALTER TABLE `category` ENABLE KEYS */;

--
-- Table structure for table `course`
--

DROP TABLE IF EXISTS `course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '课程ID',
  `title` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程标题',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '课程简介',
  `cover_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '课程封面URL',
  `difficulty` tinyint DEFAULT NULL COMMENT '难度：1-初级 2-中级 3-高级',
  `duration` int DEFAULT NULL COMMENT '课程总学时（秒数）',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-草稿 1-上线 2-下线',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course_status` (`status`),
  KEY `idx_course_difficulty` (`difficulty`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course`
--

/*!40000 ALTER TABLE `course` DISABLE KEYS */;
INSERT INTO `course` VALUES (1,'Java 基础入门','从零开始学习 Java 语法与面向对象思想','https://images.unsplash.com/photo-1518770660439-4636190af475',1,705,1,'2026-01-02 14:15:15','2026-01-31 15:13:16'),(2,'Java 面向对象进阶','深入理解 Java 面向对象设计思想与实践','https://images.unsplash.com/photo-1504639725590-34d0984388bd',2,705,1,'2026-01-02 14:15:15','2026-01-31 15:13:16'),(3,'Spring Boot 从入门到实战','基于 Spring Boot 构建企业级后端应用','https://images.unsplash.com/photo-1555949963-aa79dcee981c',2,705,1,'2026-01-02 14:15:15','2026-01-31 15:13:16'),(4,'MySQL 数据库基础','学习关系型数据库的基本原理与 SQL 编程','https://images.unsplash.com/photo-1544383835-bda2bc66a55d',1,705,1,'2026-01-02 14:15:15','2026-01-31 15:13:16'),(5,'MySQL 性能优化实战','掌握索引、执行计划与 SQL 调优技巧','https://images.unsplash.com/photo-1558494949-ef010cbdcc31',3,705,1,'2026-01-02 14:15:15','2026-01-31 15:13:16'),(6,'Vue.js 前端开发基础','使用 Vue.js 构建现代前端应用','https://images.unsplash.com/photo-1555066931-4365d14bab8c',1,705,1,'2026-01-02 14:15:15','2026-01-31 15:13:16'),(7,'Vue + Spring Boot 前后端分离实战','实现完整的前后端分离项目','https://images.unsplash.com/photo-1526378722484-cc5c5100b1a9',2,705,1,'2026-01-02 14:15:15','2026-01-31 15:13:16'),(8,'Python 数据分析入门','利用 Python 进行数据分析与可视化','https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5',1,705,1,'2026-01-02 14:15:15','2026-01-31 15:13:16'),(9,'推荐系统原理与实战','协同过滤与内容推荐算法详解','https://images.unsplash.com/photo-1534759846116-5799c33ce22a',3,705,1,'2026-01-02 14:15:15','2026-01-31 15:13:16'),(10,'Redis 核心原理与应用','深入理解 Redis 数据结构与缓存设计','https://images.unsplash.com/photo-1544197150-b99a580bb7a8',2,705,1,'2026-01-02 14:15:15','2026-01-31 15:13:16'),(11,'操作系统原理','计算机操作系统的基本概念与实现机制','https://images.unsplash.com/photo-1517433456452-f9633a875f6f',2,705,1,'2026-01-02 14:15:15','2026-01-31 15:13:16'),(12,'计算机网络基础','深入理解 TCP/IP 协议与网络通信','https://images.unsplash.com/photo-1504384308090-c894fdcc538d',1,705,1,'2026-01-02 14:15:15','2026-01-31 15:13:16'),(14,'test2','测试使用。。。','https://www.economist.com/cdn-cgi/image/width=1424,quality=80,format=auto/content-assets/images/20260228_LDP001.jpg',1,705,1,'2026-02-27 14:45:07','2026-02-27 14:47:48');
/*!40000 ALTER TABLE `course` ENABLE KEYS */;

--
-- Table structure for table `course_category_relation`
--

DROP TABLE IF EXISTS `course_category_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_category_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_id` bigint NOT NULL COMMENT '课程ID',
  `category_id` bigint NOT NULL COMMENT '类别ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_category` (`course_id`,`category_id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_category_relation`
--

/*!40000 ALTER TABLE `course_category_relation` DISABLE KEYS */;
INSERT INTO `course_category_relation` VALUES (1,1,4,'2026-01-18 15:07:17'),(2,2,4,'2026-01-18 15:07:34'),(3,3,4,'2026-01-18 15:07:42'),(4,4,4,'2026-01-18 15:07:50'),(5,5,4,'2026-01-18 15:08:02'),(6,6,1,'2026-01-18 15:08:09'),(7,7,4,'2026-01-18 15:08:26'),(8,8,2,'2026-01-18 15:08:34'),(9,9,4,'2026-01-18 15:08:43'),(10,10,4,'2026-01-18 15:08:53'),(11,11,3,'2026-01-18 15:09:03'),(12,12,3,'2026-01-18 15:09:09'),(14,14,3,'2026-02-27 14:45:07');
/*!40000 ALTER TABLE `course_category_relation` ENABLE KEYS */;

--
-- Table structure for table `course_knowledge_point`
--

DROP TABLE IF EXISTS `course_knowledge_point`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_knowledge_point` (
  `course_id` bigint NOT NULL COMMENT '课程ID',
  `kp_id` bigint NOT NULL COMMENT '知识点ID',
  PRIMARY KEY (`course_id`,`kp_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程-知识点关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_knowledge_point`
--

/*!40000 ALTER TABLE `course_knowledge_point` DISABLE KEYS */;
INSERT INTO `course_knowledge_point` VALUES (1,1),(1,2),(1,3),(1,4),(2,2),(2,3),(2,5),(3,6),(3,7),(3,8),(3,9),(4,10),(5,11),(5,12),(6,15),(7,8),(7,9),(7,15),(7,16),(8,17),(8,18),(8,19),(8,20),(9,21),(9,22),(10,13),(10,14),(11,23),(12,24),(14,1);
/*!40000 ALTER TABLE `course_knowledge_point` ENABLE KEYS */;

--
-- Table structure for table `course_tag`
--

DROP TABLE IF EXISTS `course_tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `course_id` bigint NOT NULL COMMENT '课程ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `tag_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_tag` (`course_id`,`tag_id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB AUTO_INCREMENT=43 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程-标签关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_tag`
--

/*!40000 ALTER TABLE `course_tag` DISABLE KEYS */;
INSERT INTO `course_tag` VALUES (1,1,1,'2026-01-18 15:17:47','Java'),(2,1,7,'2026-01-18 15:17:47','后端开发'),(3,1,11,'2026-01-18 15:17:47','入门'),(4,2,1,'2026-01-18 15:17:47','Java'),(5,2,7,'2026-01-18 15:17:47','后端开发'),(6,2,12,'2026-01-18 15:17:47','进阶'),(7,3,1,'2026-01-18 15:18:49','Java'),(8,3,2,'2026-01-18 15:18:54','Spring Boot'),(9,3,7,'2026-01-18 15:19:00','后端开发'),(10,3,13,'2026-01-18 15:19:06','实战'),(11,4,3,'2026-01-18 15:19:21','MySQL'),(12,4,7,'2026-01-18 15:19:21','后端开发'),(13,4,11,'2026-01-18 15:19:21','入门'),(14,5,3,'2026-01-18 15:19:21','MySQL'),(15,5,7,'2026-01-18 15:19:21','后端开发'),(16,5,13,'2026-01-18 15:19:21','实战'),(17,6,4,'2026-01-18 15:19:41','Vue'),(18,6,8,'2026-01-18 15:19:41','前端开发'),(19,6,11,'2026-01-18 15:19:41','入门'),(20,7,4,'2026-01-18 15:19:42','Vue'),(21,7,2,'2026-01-18 15:19:42','Spring Boot'),(22,7,7,'2026-01-18 15:19:42','后端开发'),(23,7,8,'2026-01-18 15:19:42','前端开发'),(24,7,13,'2026-01-18 15:19:42','实战'),(25,8,5,'2026-01-18 15:19:57','Python'),(26,8,9,'2026-01-18 15:19:57','数据分析'),(27,8,11,'2026-01-18 15:19:57','入门'),(28,9,10,'2026-01-18 15:19:57','推荐系统'),(29,9,9,'2026-01-18 15:19:57','数据分析'),(30,9,13,'2026-01-18 15:19:57','实战'),(31,10,6,'2026-01-18 15:20:10','Redis'),(32,10,7,'2026-01-18 15:20:10','后端开发'),(33,10,13,'2026-01-18 15:20:10','实战'),(34,11,14,'2026-01-18 15:20:25','计算机基础'),(35,12,14,'2026-01-18 15:20:25','计算机基础'),(39,14,11,'2026-02-27 14:45:07','入门');
/*!40000 ALTER TABLE `course_tag` ENABLE KEYS */;

--
-- Table structure for table `knowledge_dimension`
--

DROP TABLE IF EXISTS `knowledge_dimension`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_dimension` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '维度ID',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维度编码',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维度名称',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` tinyint DEFAULT '1' COMMENT '状态：1启用 0禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点维度表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `knowledge_dimension`
--

/*!40000 ALTER TABLE `knowledge_dimension` DISABLE KEYS */;
INSERT INTO `knowledge_dimension` VALUES (1,'JAVA','Java',10,1,'2026-02-15 20:21:10','2026-02-15 20:21:10'),(2,'WEB_BACKEND','Web后端',20,1,'2026-02-15 20:21:10','2026-02-15 20:21:10'),(3,'SPRING','Spring',30,1,'2026-02-15 20:21:10','2026-02-15 20:21:10'),(4,'DB','数据库',40,1,'2026-02-15 20:21:10','2026-02-15 20:21:10'),(5,'REDIS','Redis',50,1,'2026-02-15 20:21:10','2026-02-15 20:21:10'),(6,'FRONTEND','前端',60,1,'2026-02-15 20:21:10','2026-02-15 20:21:10'),(7,'PY_DATA','Python数据分析',70,1,'2026-02-15 20:21:10','2026-02-15 20:21:10'),(8,'RECSYS','推荐系统',80,1,'2026-02-15 20:21:10','2026-02-15 20:21:10'),(9,'CS_FOUNDATION','计算机基础',90,1,'2026-02-15 20:21:10','2026-02-15 20:21:10'),(10,'UNCATEGORIZED','未分类',999,1,'2026-02-15 20:21:10','2026-02-15 20:21:10');
/*!40000 ALTER TABLE `knowledge_dimension` ENABLE KEYS */;

--
-- Table structure for table `knowledge_point`
--

DROP TABLE IF EXISTS `knowledge_point`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_point` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识点ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识点名称',
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '知识点描述',
  `difficulty` tinyint DEFAULT NULL COMMENT '难度等级：1-入门 2-基础 3-进阶 4-高级',
  `dimension_id` bigint NOT NULL COMMENT '知识点维度ID',
  `status` tinyint DEFAULT '1' COMMENT '状态：1-启用 0-禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`),
  KEY `idx_dimension_id` (`dimension_id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `knowledge_point`
--

/*!40000 ALTER TABLE `knowledge_point` DISABLE KEYS */;
INSERT INTO `knowledge_point` VALUES (1,'Java 基础语法','变量、流程控制、数组等基础语法',1,1,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(2,'面向对象思想','封装、继承、多态',2,1,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(3,'集合框架','List、Set、Map 等集合容器',2,1,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(4,'异常处理','异常体系与处理机制',2,1,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(5,'JVM 原理','内存模型、垃圾回收机制',3,1,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(6,'HTTP 协议','请求响应模型与状态码',1,2,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(7,'Spring Core','IOC 与 AOP 核心机制',2,3,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(8,'Spring MVC','Web MVC 架构与控制器',2,3,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(9,'Spring Boot 核心','自动配置与 Starter 机制',2,3,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(10,'SQL 基础','DDL/DML/DQL 语句',1,4,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(11,'索引原理','B+ 树与索引设计',3,4,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(12,'执行计划分析','Explain 使用与优化',3,4,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(13,'Redis 数据结构','String/Hash/List/Set/ZSet',2,5,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(14,'缓存一致性','缓存更新策略',3,5,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(15,'Vue 基础语法','指令、组件、响应式',1,6,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(16,'前端工程化','Vite、模块化、构建工具',2,6,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(17,'Python 基础语法','变量、函数、控制流',1,7,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(18,'NumPy 基础','数组运算',2,7,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(19,'Pandas 数据处理','数据清洗与分析',2,7,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(20,'数据可视化','Matplotlib 使用',2,7,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(21,'协同过滤算法','UserCF 与 ItemCF',3,8,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(22,'内容推荐算法','TF-IDF 与向量化',3,8,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(23,'操作系统基础','进程线程与内存管理',2,9,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(24,'TCP/IP 协议','传输层协议原理',2,9,1,'2026-02-01 17:27:37','2026-02-15 20:23:55'),(25,'分布式基础','CAP 理论与一致性',3,9,1,'2026-02-01 17:27:37','2026-02-15 20:23:55');
/*!40000 ALTER TABLE `knowledge_point` ENABLE KEYS */;

--
-- Table structure for table `learning_behavior`
--

DROP TABLE IF EXISTS `learning_behavior`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `learning_behavior` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `course_id` bigint NOT NULL COMMENT '课程ID',
  `behavior_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '行为类型',
  `duration` int DEFAULT '0' COMMENT '学习时长（秒）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '行为发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_course` (`user_id`,`course_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2017481972795961377 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习行为日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `learning_behavior`
--

/*!40000 ALTER TABLE `learning_behavior` DISABLE KEYS */;
INSERT INTO `learning_behavior` VALUES (2017481972795961347,2,1,'VIEW',0,'2026-01-20 20:00:10'),(2017481972795961348,2,1,'STUDY',180,'2026-01-20 20:10:00'),(2017481972795961349,2,1,'STUDY',240,'2026-01-28 21:00:00'),(2017481972795961350,2,1,'STUDY',180,'2026-02-03 21:10:00'),(2017481972795961351,2,1,'FAVORITE',0,'2026-01-20 20:05:00'),(2017481972795961352,2,3,'VIEW',0,'2026-01-25 19:30:10'),(2017481972795961353,2,3,'STUDY',120,'2026-01-25 19:40:00'),(2017481972795961354,2,3,'STUDY',200,'2026-02-02 22:15:00'),(2017481972795961355,2,4,'VIEW',0,'2026-01-30 22:40:00'),(2017481972795961356,2,4,'STUDY',110,'2026-01-30 23:00:00'),(2017481972795961357,3,6,'VIEW',0,'2026-02-04 09:00:10'),(2017481972795961358,3,6,'STUDY',70,'2026-02-04 09:20:00'),(2017481972795961359,3,6,'FAVORITE',0,'2026-02-04 09:05:00'),(2017481972795961360,3,7,'FAVORITE',0,'2026-02-03 12:00:00'),(2017481972795961361,3,1,'VIEW',0,'2026-02-01 18:00:00'),(2017481972795961362,3,1,'STUDY',30,'2026-02-01 18:05:00'),(2017481972795961363,4,8,'VIEW',0,'2026-01-20 09:00:00'),(2017481972795961364,4,8,'STUDY',300,'2026-01-20 09:30:00'),(2017481972795961365,4,8,'STUDY',405,'2026-01-28 21:35:00'),(2017481972795961366,4,8,'FINISH',0,'2026-01-28 21:40:00'),(2017481972795961367,4,8,'FAVORITE',0,'2026-01-20 09:05:00'),(2017481972795961368,4,9,'VIEW',0,'2026-01-23 10:00:00'),(2017481972795961369,4,9,'STUDY',705,'2026-01-31 20:05:00'),(2017481972795961370,4,9,'FINISH',0,'2026-01-31 20:10:00'),(2017481972795961371,4,5,'VIEW',0,'2026-02-01 08:00:00'),(2017481972795961372,4,5,'STUDY',200,'2026-02-01 08:40:00'),(2017481972795961373,4,5,'STUDY',220,'2026-02-02 08:30:00'),(2017481972795961374,5,11,'VIEW',0,'2026-02-03 13:50:00'),(2017481972795961375,5,11,'STUDY',15,'2026-02-03 14:00:00'),(2017481972795961376,5,12,'VIEW',0,'2026-02-02 16:00:00');
/*!40000 ALTER TABLE `learning_behavior` ENABLE KEYS */;

--
-- Table structure for table `tag`
--

DROP TABLE IF EXISTS `tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
  `type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签类型（方向/技术/难度等）',
  `status` tinyint DEFAULT '1' COMMENT '状态：1-启用，0-禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tag_name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程标签表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tag`
--

/*!40000 ALTER TABLE `tag` DISABLE KEYS */;
INSERT INTO `tag` VALUES (1,'Java','TECH',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(2,'Spring Boot','TECH',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(3,'MySQL','TECH',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(4,'Vue','TECH',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(5,'Python','TECH',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(6,'Redis','TECH',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(7,'后端开发','FIELD',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(8,'前端开发','FIELD',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(9,'数据分析','FIELD',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(10,'推荐系统','FIELD',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(11,'入门','LEVEL',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(12,'进阶','LEVEL',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(13,'实战','LEVEL',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(14,'计算机基础','THEORY',1,'2026-01-18 15:16:53','2026-01-18 15:16:53');
/*!40000 ALTER TABLE `tag` ENABLE KEYS */;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录账号',
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '加密密码',
  `nickname` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STUDENT' COMMENT '角色：STUDENT / TEACHER / ADMIN',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1-正常 0-禁用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-未删除 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (1,'admin','123456','sy','123456@shou.come','18621503807','ADMIN',1,'2025-12-28 20:06:20','2026-02-04 22:26:45',0),(2,'stu_zhangsan','123456','张三','zhangsan@test.com','01000000002','STUDENT',1,'2026-01-10 10:00:00','2026-01-10 10:00:00',0),(3,'stu_lisi','123456','李四','lisi@test.com','01000000003','STUDENT',1,'2026-01-11 10:00:00','2026-01-11 10:00:00',0),(4,'stu_wangwu','123456','王五','wangwu@test.com','01000000004','STUDENT',1,'2026-01-12 10:00:00','2026-01-12 10:00:00',0),(5,'stu_zhaoliu','123456','赵六','zhaoliu@test.com','01000000005','STUDENT',1,'2026-01-13 10:00:00','2026-01-13 10:00:00',0),(6,'stu_newbie','123456','新同学','newbie@test.com','01000000006','STUDENT',1,'2026-02-01 09:00:00','2026-02-01 09:00:00',0);
/*!40000 ALTER TABLE `user` ENABLE KEYS */;

--
-- Table structure for table `user_course_relation`
--

DROP TABLE IF EXISTS `user_course_relation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_course_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `course_id` bigint NOT NULL COMMENT '课程ID',
  `progress` tinyint DEFAULT '0' COMMENT '学习进度(0-100)',
  `learned_seconds` int DEFAULT '0' COMMENT '已学习时长(秒)',
  `status` tinyint DEFAULT '0' COMMENT '学习状态：0-未开始，1-学习中，2-已完成',
  `last_learn_time` datetime DEFAULT NULL COMMENT '最近一次学习时间',
  `complete_time` datetime DEFAULT NULL COMMENT '完成时间',
  `is_favorite` tinyint DEFAULT '0' COMMENT '是否收藏：0-否，1-是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '首次学习时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `progress_seconds` int NOT NULL DEFAULT '0' COMMENT '当前观看到第几秒',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_course` (`user_id`,`course_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2020755789312118787 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-课程学习关系表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_course_relation`
--

/*!40000 ALTER TABLE `user_course_relation` DISABLE KEYS */;
INSERT INTO `user_course_relation` VALUES (2017589199745622018,2,1,85,600,1,'2026-02-03 21:10:00',NULL,1,'2026-01-20 20:00:00','2026-02-03 21:10:00',600),(2017589199745622019,2,3,45,320,1,'2026-02-02 22:15:00',NULL,0,'2026-01-25 19:30:00','2026-02-02 22:15:00',320),(2017589199745622020,2,4,15,110,1,'2026-01-30 23:00:00',NULL,0,'2026-01-30 22:40:00','2026-01-30 23:00:00',110),(2017589199745622021,3,6,10,70,1,'2026-02-04 09:20:00',NULL,1,'2026-02-04 09:00:00','2026-02-04 09:20:00',70),(2017589199745622022,3,7,0,0,0,NULL,NULL,1,'2026-02-03 12:00:00','2026-02-03 12:00:00',0),(2017589199745622023,3,1,5,30,1,'2026-02-01 18:05:00',NULL,0,'2026-02-01 18:00:00','2026-02-01 18:05:00',30),(2017589199745622024,4,8,100,705,2,'2026-01-28 21:40:00','2026-01-28 21:40:00',1,'2026-01-20 09:00:00','2026-01-28 21:40:00',705),(2017589199745622025,4,9,100,705,2,'2026-01-31 20:10:00','2026-01-31 20:10:00',0,'2026-01-23 10:00:00','2026-01-31 20:10:00',705),(2017589199745622026,4,5,60,420,1,'2026-02-02 08:30:00',NULL,0,'2026-02-01 08:00:00','2026-02-02 08:30:00',420),(2017589199745622027,5,11,2,15,1,'2026-02-03 14:00:00',NULL,0,'2026-02-03 13:50:00','2026-02-03 14:00:00',15),(2017589199745622028,5,12,0,0,0,NULL,NULL,0,'2026-02-02 16:00:00','2026-02-02 16:00:00',0);
/*!40000 ALTER TABLE `user_course_relation` ENABLE KEYS */;

--
-- Table structure for table `video`
--

DROP TABLE IF EXISTS `video`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `video` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '视频ID',
  `course_id` bigint NOT NULL COMMENT '所属课程ID',
  `title` varchar(255) NOT NULL COMMENT '视频标题（如：第一章 - 入门介绍）',
  `video_path` varchar(512) NOT NULL COMMENT '视频文件路径（相对于 static 目录，如：video/java_01.mp4）',
  `duration_seconds` int DEFAULT '0' COMMENT '视频时长（秒），可用于前端显示 00:00）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='视频表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `video`
--

/*!40000 ALTER TABLE `video` DISABLE KEYS */;
INSERT INTO `video` VALUES (1,12,'计算机网络基础','1',705,'2026-01-28 15:48:03','2026-02-25 23:39:33'),(2,1,'Java 基础入门','1',705,'2026-01-31 14:56:20','2026-02-25 23:39:33'),(3,2,'Java 面向对象进阶','1',705,'2026-01-31 14:56:20','2026-02-25 23:39:33'),(4,3,'Spring Boot 从入门到实战','1',705,'2026-01-31 14:56:20','2026-02-25 23:39:33'),(5,4,'MySQL 数据库基础','1',705,'2026-01-31 14:56:20','2026-02-25 23:39:33'),(6,5,'MySQL 性能优化实战','1',705,'2026-01-31 14:56:20','2026-02-25 23:39:33'),(7,6,'Vue.js 前端开发基础','1',705,'2026-01-31 14:56:20','2026-02-25 23:39:33'),(8,7,'Vue + Spring Boot 前后端分离实战','1',705,'2026-01-31 14:56:20','2026-02-25 23:39:33'),(9,8,'Python 数据分析入门','1',705,'2026-01-31 14:56:20','2026-02-25 23:39:33'),(10,9,'推荐系统原理与实战','1',705,'2026-01-31 14:56:20','2026-02-25 23:39:33'),(11,10,'Redis 核心原理与应用','1',705,'2026-01-31 14:56:20','2026-02-25 23:39:33'),(12,11,'操作系统原理','1',705,'2026-01-31 14:56:20','2026-02-25 23:39:33'),(13,13,'test','13/9eb94ce94611453491f597fbd4590e27.mp4',705,'2026-02-25 23:24:47','2026-02-27 14:45:54'),(14,14,'test2','14/6bf74f400b25470a99820f7d410acde4.mp4',705,'2026-02-27 14:45:07','2026-02-27 14:45:07');
/*!40000 ALTER TABLE `video` ENABLE KEYS */;

--
-- Dumping routines for database 'course_db'
--
--
-- WARNING: can't read the INFORMATION_SCHEMA.libraries table. It's most probably an old server 8.0.44.
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-03-04 23:42:47
