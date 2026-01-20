-- MySQL dump 10.13  Distrib 8.0.44, for Linux (x86_64)
--
-- Host: localhost    Database: course_db
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
  `behavior_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '行为类型',
  `weight` double NOT NULL COMMENT '权重值',
  PRIMARY KEY (`behavior_type`),
  KEY `idx_behavior_type` (`behavior_type`),
  CONSTRAINT `chk_weight_positive` CHECK ((`weight` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行为权重配置表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `behavior_weight`
--

LOCK TABLES `behavior_weight` WRITE;
/*!40000 ALTER TABLE `behavior_weight` DISABLE KEYS */;
INSERT INTO `behavior_weight` VALUES ('FAVOURITE',4),('FINISH',5),('STUDY',3),('VIEW',1);
/*!40000 ALTER TABLE `behavior_weight` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `category`
--

DROP TABLE IF EXISTS `category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类别名称',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '类别描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `category`
--

LOCK TABLES `category` WRITE;
/*!40000 ALTER TABLE `category` DISABLE KEYS */;
INSERT INTO `category` VALUES (1,'前端',NULL,'2026-01-18 14:56:48','2026-01-18 14:56:48'),(2,'数据',NULL,'2026-01-18 14:57:02','2026-01-18 14:57:02'),(3,'计算机基础',NULL,'2026-01-18 14:57:10','2026-01-18 14:57:10'),(4,'后端',NULL,'2026-01-18 14:57:20','2026-01-18 14:57:20');
/*!40000 ALTER TABLE `category` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course`
--

DROP TABLE IF EXISTS `course`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '课程ID',
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课程标题',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '课程简介',
  `cover_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '课程封面URL',
  `difficulty` tinyint DEFAULT NULL COMMENT '难度：1-初级 2-中级 3-高级',
  `duration` int DEFAULT NULL COMMENT '课程总学时（分钟）',
  `status` tinyint DEFAULT '1' COMMENT '状态：0-下线 1-上线',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_course_status` (`status`),
  KEY `idx_course_difficulty` (`difficulty`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course`
--

LOCK TABLES `course` WRITE;
/*!40000 ALTER TABLE `course` DISABLE KEYS */;
INSERT INTO `course` VALUES (1,'Java 基础入门','从零开始学习 Java 语法与面向对象思想','https://images.unsplash.com/photo-1518770660439-4636190af475',1,240,1,'2026-01-02 14:15:15','2026-01-18 15:31:13'),(2,'Java 面向对象进阶','深入理解 Java 面向对象设计思想与实践','https://images.unsplash.com/photo-1504639725590-34d0984388bd',2,300,1,'2026-01-02 14:15:15','2026-01-18 15:31:13'),(3,'Spring Boot 从入门到实战','基于 Spring Boot 构建企业级后端应用','https://images.unsplash.com/photo-1555949963-aa79dcee981c',2,360,1,'2026-01-02 14:15:15','2026-01-18 15:31:13'),(4,'MySQL 数据库基础','学习关系型数据库的基本原理与 SQL 编程','https://images.unsplash.com/photo-1544383835-bda2bc66a55d',1,200,1,'2026-01-02 14:15:15','2026-01-18 15:31:13'),(5,'MySQL 性能优化实战','掌握索引、执行计划与 SQL 调优技巧','https://images.unsplash.com/photo-1558494949-ef010cbdcc31',3,420,1,'2026-01-02 14:15:15','2026-01-18 15:31:13'),(6,'Vue.js 前端开发基础','使用 Vue.js 构建现代前端应用','https://images.unsplash.com/photo-1555066931-4365d14bab8c',1,260,1,'2026-01-02 14:15:15','2026-01-18 15:31:13'),(7,'Vue + Spring Boot 前后端分离实战','实现完整的前后端分离项目','https://images.unsplash.com/photo-1526378722484-cc5c5100b1a9',2,380,1,'2026-01-02 14:15:15','2026-01-18 15:31:13'),(8,'Python 数据分析入门','利用 Python 进行数据分析与可视化','https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5',1,300,1,'2026-01-02 14:15:15','2026-01-18 15:31:13'),(9,'推荐系统原理与实战','协同过滤与内容推荐算法详解','https://images.unsplash.com/photo-1534759846116-5799c33ce22a',3,480,1,'2026-01-02 14:15:15','2026-01-18 15:31:13'),(10,'Redis 核心原理与应用','深入理解 Redis 数据结构与缓存设计','https://images.unsplash.com/photo-1544197150-b99a580bb7a8',2,320,1,'2026-01-02 14:15:15','2026-01-18 15:31:13'),(11,'操作系统原理','计算机操作系统的基本概念与实现机制','https://images.unsplash.com/photo-1517433456452-f9633a875f6f',2,400,1,'2026-01-02 14:15:15','2026-01-18 15:31:13'),(12,'计算机网络基础','深入理解 TCP/IP 协议与网络通信','https://images.unsplash.com/photo-1504384308090-c894fdcc538d',1,360,1,'2026-01-02 14:15:15','2026-01-18 15:31:13');
/*!40000 ALTER TABLE `course` ENABLE KEYS */;
UNLOCK TABLES;

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
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_category_relation`
--

LOCK TABLES `course_category_relation` WRITE;
/*!40000 ALTER TABLE `course_category_relation` DISABLE KEYS */;
INSERT INTO `course_category_relation` VALUES (1,1,4,'2026-01-18 15:07:17'),(2,2,4,'2026-01-18 15:07:34'),(3,3,4,'2026-01-18 15:07:42'),(4,4,4,'2026-01-18 15:07:50'),(5,5,4,'2026-01-18 15:08:02'),(6,6,1,'2026-01-18 15:08:09'),(7,7,4,'2026-01-18 15:08:26'),(8,8,2,'2026-01-18 15:08:34'),(9,9,4,'2026-01-18 15:08:43'),(10,10,4,'2026-01-18 15:08:53'),(11,11,3,'2026-01-18 15:09:03'),(12,12,3,'2026-01-18 15:09:09');
/*!40000 ALTER TABLE `course_category_relation` ENABLE KEYS */;
UNLOCK TABLES;

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

LOCK TABLES `course_knowledge_point` WRITE;
/*!40000 ALTER TABLE `course_knowledge_point` DISABLE KEYS */;
/*!40000 ALTER TABLE `course_knowledge_point` ENABLE KEYS */;
UNLOCK TABLES;

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
  `tag_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_course_tag` (`course_id`,`tag_id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB AUTO_INCREMENT=36 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课程-标签关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_tag`
--

LOCK TABLES `course_tag` WRITE;
/*!40000 ALTER TABLE `course_tag` DISABLE KEYS */;
INSERT INTO `course_tag` VALUES (1,1,1,'2026-01-18 15:17:47','Java'),(2,1,7,'2026-01-18 15:17:47','后端开发'),(3,1,11,'2026-01-18 15:17:47','入门'),(4,2,1,'2026-01-18 15:17:47','Java'),(5,2,7,'2026-01-18 15:17:47','后端开发'),(6,2,12,'2026-01-18 15:17:47','进阶'),(7,3,1,'2026-01-18 15:18:49','Java'),(8,3,2,'2026-01-18 15:18:54','Spring Boot'),(9,3,7,'2026-01-18 15:19:00','后端开发'),(10,3,13,'2026-01-18 15:19:06','实战'),(11,4,3,'2026-01-18 15:19:21','MySQL'),(12,4,7,'2026-01-18 15:19:21','后端开发'),(13,4,11,'2026-01-18 15:19:21','入门'),(14,5,3,'2026-01-18 15:19:21','MySQL'),(15,5,7,'2026-01-18 15:19:21','后端开发'),(16,5,13,'2026-01-18 15:19:21','实战'),(17,6,4,'2026-01-18 15:19:41','Vue'),(18,6,8,'2026-01-18 15:19:41','前端开发'),(19,6,11,'2026-01-18 15:19:41','入门'),(20,7,4,'2026-01-18 15:19:42','Vue'),(21,7,2,'2026-01-18 15:19:42','Spring Boot'),(22,7,7,'2026-01-18 15:19:42','后端开发'),(23,7,8,'2026-01-18 15:19:42','前端开发'),(24,7,13,'2026-01-18 15:19:42','实战'),(25,8,5,'2026-01-18 15:19:57','Python'),(26,8,9,'2026-01-18 15:19:57','数据分析'),(27,8,11,'2026-01-18 15:19:57','入门'),(28,9,10,'2026-01-18 15:19:57','推荐系统'),(29,9,9,'2026-01-18 15:19:57','数据分析'),(30,9,13,'2026-01-18 15:19:57','实战'),(31,10,6,'2026-01-18 15:20:10','Redis'),(32,10,7,'2026-01-18 15:20:10','后端开发'),(33,10,13,'2026-01-18 15:20:10','实战'),(34,11,14,'2026-01-18 15:20:25','计算机基础'),(35,12,14,'2026-01-18 15:20:25','计算机基础');
/*!40000 ALTER TABLE `course_tag` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `knowledge_point`
--

DROP TABLE IF EXISTS `knowledge_point`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `knowledge_point` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识点ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '知识点名称',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '知识点描述',
  `difficulty` tinyint DEFAULT NULL COMMENT '难度等级：1-入门 2-基础 3-进阶 4-高级',
  `status` tinyint DEFAULT '1' COMMENT '状态：1-启用 0-禁用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `knowledge_point`
--

LOCK TABLES `knowledge_point` WRITE;
/*!40000 ALTER TABLE `knowledge_point` DISABLE KEYS */;
/*!40000 ALTER TABLE `knowledge_point` ENABLE KEYS */;
UNLOCK TABLES;

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
  `behavior_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '行为类型',
  `duration` int DEFAULT '0' COMMENT '学习时长（秒）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '行为发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_course` (`user_id`,`course_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2007033517723971586 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学习行为日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `learning_behavior`
--

LOCK TABLES `learning_behavior` WRITE;
/*!40000 ALTER TABLE `learning_behavior` DISABLE KEYS */;
INSERT INTO `learning_behavior` VALUES (2006273621185359874,1,1,'VIEW',300,'2026-01-02 14:22:36'),(2006273621185359875,1,1,'STUDY',1800,'2026-01-03 14:22:36'),(2006273621185359876,1,2,'STUDY',2400,'2026-01-04 14:22:36'),(2006273621185359877,1,3,'STUDY',3600,'2026-01-05 14:22:36'),(2006273621185359878,1,3,'FINISH',4200,'2026-01-06 14:22:36'),(2006273621185359879,1,10,'VIEW',200,'2026-01-07 14:22:36'),(2006273621185359880,2,6,'VIEW',400,'2026-01-02 14:22:36'),(2006273621185359881,2,6,'STUDY',2000,'2026-01-03 14:22:36'),(2006273621185359882,2,7,'STUDY',3200,'2026-01-04 14:22:36'),(2006273621185359883,2,7,'FINISH',3600,'2026-01-05 14:22:36'),(2006273621185359884,2,3,'VIEW',300,'2026-01-06 14:22:36'),(2006273621185359885,3,4,'VIEW',500,'2026-01-02 14:22:36'),(2006273621185359886,3,4,'STUDY',2400,'2026-01-03 14:22:36'),(2006273621185359887,3,5,'STUDY',3600,'2026-01-04 14:22:36'),(2006273621185359888,3,5,'FINISH',4000,'2026-01-05 14:22:36'),(2006273621185359889,3,10,'STUDY',1800,'2026-01-06 14:22:36'),(2006273621185359890,4,9,'VIEW',600,'2026-01-02 14:22:36'),(2006273621185359891,4,9,'STUDY',3000,'2026-01-03 14:22:36'),(2006273621185359892,4,9,'FINISH',4800,'2026-01-04 14:22:36'),(2006273621185359893,4,8,'STUDY',2600,'2026-01-05 14:22:36'),(2006273621185359894,5,1,'STUDY',2000,'2026-01-02 14:22:36'),(2006273621185359895,5,3,'STUDY',2800,'2026-01-03 14:22:36'),(2006273621185359896,5,6,'VIEW',500,'2026-01-04 14:22:36'),(2006273621185359897,5,10,'STUDY',2200,'2026-01-05 14:22:36'),(2006273621185359898,5,10,'FAVOURITE',0,'2026-01-06 14:22:36');
/*!40000 ALTER TABLE `learning_behavior` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tag`
--

DROP TABLE IF EXISTS `tag`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
  `type` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签类型（方向/技术/难度等）',
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

LOCK TABLES `tag` WRITE;
/*!40000 ALTER TABLE `tag` DISABLE KEYS */;
INSERT INTO `tag` VALUES (1,'Java','TECH',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(2,'Spring Boot','TECH',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(3,'MySQL','TECH',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(4,'Vue','TECH',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(5,'Python','TECH',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(6,'Redis','TECH',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(7,'后端开发','FIELD',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(8,'前端开发','FIELD',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(9,'数据分析','FIELD',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(10,'推荐系统','FIELD',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(11,'入门','LEVEL',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(12,'进阶','LEVEL',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(13,'实战','LEVEL',1,'2026-01-18 15:16:53','2026-01-18 15:16:53'),(14,'计算机基础','THEORY',1,'2026-01-18 15:16:53','2026-01-18 15:16:53');
/*!40000 ALTER TABLE `tag` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录账号',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '加密密码',
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '昵称',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STUDENT' COMMENT '角色：STUDENT / TEACHER / ADMIN',
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

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (1,'admin','123456',NULL,'123456@shou.come','18621503807','STUDENT',1,'2025-12-28 20:06:20','2025-12-28 20:06:20',0),(2,'test2','123456',NULL,'123456@shou.come','13512434134','STUDENT',1,'2026-01-17 19:37:47','2026-01-17 19:38:28',0),(3,'test3','123456',NULL,'123456@shou.come','14535342652','STUDENT',1,'2026-01-17 19:38:33','2026-01-17 19:38:42',0),(4,'test4','123456',NULL,'123456@shou.come','16345643734','STUDENT',1,'2026-01-17 19:39:01','2026-01-17 19:39:01',0),(5,'test_5','123456',NULL,'123456@shou.come','15637573547','STUDENT',1,'2026-01-17 19:39:42','2026-01-17 19:51:47',0),(6,'test_6','shiyang123',NULL,'1499172392@qq.com','18621503807','STUDENT',1,'2026-01-17 22:03:18','2026-01-17 22:03:18',0);
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;

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
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_course` (`user_id`,`course_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户-课程学习关系表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_course_relation`
--

LOCK TABLES `user_course_relation` WRITE;
/*!40000 ALTER TABLE `user_course_relation` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_course_relation` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-01-20 23:21:36
