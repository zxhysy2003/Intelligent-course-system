package com.sy.course_system.outbox;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.sy.course_system.outbox.support.LearningOutboxFixtureMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.mybatis.spring.annotation.MapperScan;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.containers.GenericContainer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sy.course_system.config.*;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.common.UserInfo;
import com.sy.course_system.enums.*;
import com.sy.course_system.service.*;
import com.sy.course_system.service.impl.*;
import com.sy.course_system.recommend.RecommendCacheInvalidator;
import com.sy.course_system.controller.client.LearningBehaviorRecordController;

/** 真实 MySQL 事务、Redis Lua、Neo4j 写锁；不对外部系统使用 mock 替代一致性验证。 */
@Testcontainers
@SpringBootTest(classes = LearningOutboxIntegrationTest.Config.class)
@AutoConfigureMockMvc(addFilters = false)
class LearningOutboxIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36").withCommand("--log-bin-trust-function-creators=1");
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379);
    @Container static final Neo4jContainer<?> NEO = new Neo4jContainer<>("neo4j:5.26-community").withAdminPassword("outbox-test-password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", MYSQL::getJdbcUrl);
        p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword);
        p.add("spring.data.redis.host", REDIS::getHost);
        p.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        p.add("spring.data.redis.password", () -> "");
        p.add("spring.neo4j.uri", NEO::getBoltUrl);
        p.add("spring.neo4j.authentication.username", () -> "neo4j");
        p.add("spring.neo4j.authentication.password", () -> "outbox-test-password");
        p.add("learning.outbox.enabled", () -> false);
        p.add("logging.level.org.springframework.transaction", () -> "WARN");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan({"com.sy.course_system.mapper", "com.sy.course_system.outbox.support"})
    @EnableConfigurationProperties({RecommendProperties.class, LearningOutboxProperties.class})
    @Import({TransactionManagerConfig.class, RedisConfiguration.class, MyMetaObjectHandler.class,
        LearningBehaviorServiceImpl.class, UserCourseServiceImpl.class, RecommendScoreSnapshotServiceImpl.class,
        LearningOutboxWriter.class, LearningOutboxStore.class, LearningOutboxHandler.class,
        LearningHotUpdater.class, LearningMasteryUpdater.class, RecommendCacheInvalidator.class,
        LearningBehaviorRecordController.class})
    static class Config {
        // 仅隔离无关课程/视频服务，SQL 查询仍读取真实业务表。
        @Bean CourseService courses(LearningOutboxFixtureMapper fixtures) {
            CourseService service = org.mockito.Mockito.mock(CourseService.class);
            org.mockito.Mockito.when(service.getKnowledgePointIdsByCourseId(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(a -> fixtures.selectKnowledgePointIds(a.getArgument(0)));
            return service;
        }
        @Bean VideoService videos(LearningOutboxFixtureMapper fixtures) {
            VideoService service = org.mockito.Mockito.mock(VideoService.class);
            org.mockito.Mockito.when(service.getVideoDurationInSeconds(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(a -> fixtures.selectVideoDuration(a.getArgument(0)));
            return service;
        }
    }

    @Autowired LearningOutboxFixtureMapper fixtures;
    @Autowired LearningBehaviorService behavior;
    @Autowired LearningOutboxStore store;
    @Autowired LearningOutboxHandler handler;
    @Autowired LearningHotUpdater hot;
    @Autowired LearningMasteryUpdater mastery;
    @Autowired LearningOutboxWriter writer;
    @Autowired ObjectMapper json;
    @Autowired RedisTemplate<String,Object> redis;
    @Autowired StringRedisTemplate strings;
    @Autowired Neo4jClient neo;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;
    @Autowired RecommendCacheInvalidator invalidator;
    @Autowired RecommendScoreSnapshotService snapshots;

    @BeforeEach
    void reset() {
        fixtures.clearTasks();
        fixtures.clearBehaviors();
        fixtures.restoreUser();
        fixtures.publishCourse();
        fixtures.resetVideoDuration();
        fixtures.resetRelation();
        try (var connection = redis.getConnectionFactory().getConnection()) { connection.serverCommands().flushDb(); }
        neo.query("MATCH (n) DETACH DELETE n").run();
        neo.query("CREATE (:User {id:2})").run();
        neo.query("UNWIND [1,2,3,4] AS id CREATE (:Knowledge {id:id})").run();
        UserContext.set(new UserInfo(2L, "outbox-test", "USER"));
    }
    @AfterEach void clear() { UserContext.clear(); }

    @Test void rollbackIncludesBusinessCooldownAndTasks() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(s -> {
            behavior.recordBehavior(1L, LearnBehaviorType.VIEW, null, null);
            assertEquals(2, fixtures.countTasks());
            throw new IllegalStateException("rollback");
        }));
        assertEquals(0, fixtures.countTasks());
        assertEquals(0L, fixtures.countBehaviors());
        assertNull(fixtures.selectLastViewTime());
    }

    @Test void taskInsertFailureRollsBackStudyAndFinish() {
        fixtures.createOutboxFailureTrigger();
        try {
            assertThrows(RuntimeException.class, () -> behavior.recordBehavior(1L, LearnBehaviorType.STUDY, 600, "finish-fail"));
            assertEquals(0, fixtures.countTasks());
            assertEquals(0, seconds());
            assertEquals(0L, fixtures.countBehaviors());
        } finally { fixtures.dropOutboxFailureTrigger(); }
    }

    @Test void finishInsertFailureLeavesNoExternalEffects() {
        fixtures.createFinishFailureTrigger();
        try {
            assertThrows(RuntimeException.class, () -> behavior.recordBehavior(1L, LearnBehaviorType.STUDY, 600, "finish-fail"));
            assertEquals(0, seconds());
            assertEquals(0, fixtures.countTasks());
            assertNull(redis.opsForZSet().score("course:hot", 1L));
        } finally { fixtures.dropFinishFailureTrigger(); }
    }

    @Test void concurrentReplayAndFinishOnlyProduceOneEvent() throws Exception {
        concurrently(8, () -> behavior.recordBehavior(1L, LearnBehaviorType.STUDY, 600, "same-event"));
        assertEquals(600, seconds());
        assertEquals(4, fixtures.countTasks());
        assertEquals(1L, fixtures.countFinishBehaviors());
        drain();
        assertEquals(4, store.count("DONE"));
        assertEquals(2 + 10.0/30, redis.opsForZSet().score("course:hot", 1L), 1e-9);
    }

    @Test void concurrentViewAndFavoriteAreSerialized() throws Exception {
        concurrently(8, () -> behavior.recordBehavior(1L, LearnBehaviorType.VIEW, null, null));
        assertEquals(2, fixtures.countTasks());
        concurrently(8, () -> behavior.recordBehavior(1L, LearnBehaviorType.FAVORITE, null, null));
        assertEquals(5, fixtures.countTasks());
    }

    @Test void crashAfterRedisSuccessCanBeReclaimedWithoutDoubleIncrement() throws Exception {
        behavior.recordBehavior(1L, LearnBehaviorType.VIEW, null, null);
        LearningOutboxTask task = claimType("HOT_INCREMENT");
        assertFalse(handler.handle(task));
        expire(task);
        LearningOutboxTask retry = store.claim();
        assertEquals(task.id(), retry.id());
        assertFalse(store.complete(task, false));
        assertFalse(store.renew(task));
        assertFalse(handler.handle(retry));
        assertTrue(store.complete(retry, false));
        assertEquals(0.5, redis.opsForZSet().score("course:hot", 1L));
        assertTrue(strings.hasKey("learning:outbox:hot:" + task.id()));
    }

    @Test void dependenciesWaitAndMissingGraphDataRetries() throws Exception {
        behavior.recordBehavior(1L, LearnBehaviorType.STUDY, 600, "missing-graph");
        neo.query("MATCH (k:Knowledge {id:4}) DELETE k").run();
        LearningOutboxTask graph = claimType("MASTERY_UPDATE");
        Exception failure = assertThrows(Exception.class, () -> handler.handle(graph));
        store.fail(graph, failure);
        assertEquals(0L, neo.query("MATCH ()-[m:MASTERED]->() RETURN count(m)").fetchAs(Long.class).one().orElseThrow());
        drain();
        assertEquals(2, store.count("PENDING")); // 图谱和依赖它的缓存任务
        neo.query("CREATE (:Knowledge {id:4})").run();
        fixtures.makePendingDue();
        drain();
        assertEquals(4, store.count("DONE"));
        assertNotNull(strings.opsForValue().get("recommend:v2:version:user:2"));
    }

    @Test void highestMasteryWinsUnderConcurrentAndRepeatedEvents() throws Exception {
        var time = LocalDateTime.of(2026, 9, 6, 12, 0);
        concurrently(8, () -> { mastery.update(2L, LearningOutboxPayload.of(0, List.of(1L),
                Thread.currentThread().getId() % 2 == 0 ? 0.6 : 0.9, time, null)); return null; });
        mastery.update(2L, LearningOutboxPayload.of(0, List.of(1L), 0.9, time, null));
        mastery.update(2L, LearningOutboxPayload.of(0, List.of(1L), 0.6, time.plusDays(1), null));
        assertEquals(0.9, neo.query("MATCH ()-[m:MASTERED]->() RETURN m.score").fetchAs(Double.class).one().orElseThrow());
        assertEquals(time, neo.query("MATCH ()-[m:MASTERED]->() RETURN m.updatedAt").fetchAs(LocalDateTime.class).one().orElseThrow());
    }

    @Test void hotScriptValidatesTypesBeforeIncrement() {
        strings.opsForList().leftPush("learning:outbox:hot:bad", "wrong type");
        assertThrows(RuntimeException.class, () -> hot.increment("bad", 1L, 5));
        assertNull(redis.opsForZSet().score("course:hot", 1L));
    }

    @Test void deadTaskCanBeReplayedWithOriginalId() throws Exception {
        behavior.recordBehavior(1L, LearnBehaviorType.VIEW, null, null);
        LearningOutboxTask task = claimType("HOT_INCREMENT");
        fixtures.exhaustTask(task.id());
        var finalAttempt = new LearningOutboxTask(task.id(), task.type(), task.userId(), task.courseId(),task.payload(),20,task.leaseToken());
        store.fail(finalAttempt, new IllegalStateException("injected"));
        assertEquals(1, store.count("DEAD"));
        fixtures.replayDeadTask(task.id());
        drain();
        assertEquals(0.5, redis.opsForZSet().score("course:hot", 1L));
    }

    @Test void deletedUserAndOfflineCourseAreSkipped() throws Exception {
        behavior.recordBehavior(1L, LearnBehaviorType.FAVORITE, null, null);
        fixtures.unpublishCourse();
        var hotTask = claimType("HOT_INCREMENT");
        assertTrue(handler.handle(hotTask));
        store.complete(hotTask, true);
        fixtures.deleteUser();
        drain();
        assertEquals(3, store.count("SKIPPED"));
    }

    @Test void httpSubmissionWorksDuringRedisFailureAndRecovers() throws Exception {
        // WRONGTYPE 模拟真实 Redis 返回错误，主请求不触碰 Redis。
        strings.opsForValue().set("course:hot", "unavailable");
        mvc.perform(post("/api/v1/learning-behaviors").contentType("application/json")
                .content("{\"courseId\":1,\"behaviorType\":\"STUDY\",\"duration\":60,\"eventId\":\"http-study\"}"))
                .andExpect(status().isOk());
        assertEquals(60, seconds());
        LearningOutboxTask task = claimType("HOT_INCREMENT");
        store.fail(task, assertThrows(Exception.class, () -> handler.handle(task)));
        strings.delete("course:hot");
        fixtures.makePendingDue();
        drain();
        assertEquals(3, store.count("DONE"));
    }

    @Test void concurrentClaimsHaveDistinctLeasesAndExpiredOwnersCannotWrite() throws Exception {
        behavior.recordBehavior(1L, LearnBehaviorType.FAVORITE, null, null);
        var claimed = new java.util.concurrent.ConcurrentLinkedQueue<LearningOutboxTask>();
        concurrently(8, () -> { var task = store.claim(); if (task != null) claimed.add(task); return null; });
        // SKIP LOCKED 可以暂时跳过其他领取事务扫描过的行；下一轮必须能继续领取。
        LearningOutboxTask remaining;
        while ((remaining = store.claim()) != null) claimed.add(remaining);
        assertEquals(2, claimed.size()); // 缓存依赖快照，暂不可领取。
        assertEquals(2, claimed.stream().map(LearningOutboxTask::id).distinct().count());
        for (var task : claimed) {
            assertTrue(store.renew(task));
            expire(task);
            var retry = store.claim();
            assertEquals(task.id(), retry.id());
            store.fail(task, new IllegalStateException("stale owner"));
            assertFalse(store.complete(task, false));
            store.complete(retry, handler.handle(retry));
        }
        drain();
        assertEquals(3, store.count("DONE"));
    }

    @Test void crashedFinalAttemptBecomesDeadAndWriterRequiresTransaction() {
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> writer.enqueue(2L,1L,LearningOutboxPayload.of(0,List.of(),null,null,null),false,false));
        behavior.recordBehavior(1L, LearnBehaviorType.VIEW, null, null);
        var task = store.claim();
        fixtures.expireExhaustedTask(task.id());
        store.claim();
        assertEquals(1, store.count("DEAD"));
    }

    @Test void realRedisOutageDoesNotFailLearningRequest() throws Exception {
        REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
        LearningOutboxTask task;
        try {
            behavior.recordBehavior(1L, LearnBehaviorType.STUDY, 60, "redis-down");
            assertEquals(60, seconds());
            fixtures.prioritizeHotTasks();
            task = claimType("HOT_INCREMENT");
            store.fail(task, assertThrows(Exception.class, () -> handler.handle(task)));
        } finally {
            REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
        }
        fixtures.makePendingDue();
        drain();
        assertEquals(3, store.count("DONE"));
        assertEquals(1.0/30, redis.opsForZSet().score("course:hot",1L),1e-9);
    }

    @Test void failedSoftInvalidationDoesNotConsumeThrottle() {
        strings.opsForValue().set("recommend:v2:version:user:2", "not-an-integer");
        assertThrows(RuntimeException.class, () -> invalidator.invalidateFromOutbox(2L, "soft"));
        assertFalse(strings.hasKey("recommend:invalidate:study:user:2"));
        strings.delete("recommend:v2:version:user:2");
        invalidator.invalidateFromOutbox(2L, "soft");
        assertEquals("1", strings.opsForValue().get("recommend:v2:version:user:2"));
    }

    @Test void enrollmentJsonDoesNotExposeInternalCooldown() throws Exception {
        var relation = new com.sy.course_system.entity.UserCourseRelation();
        relation.setLastViewRecordedAt(LocalDateTime.now());
        assertFalse(json.writeValueAsString(relation).contains("lastViewRecordedAt"));
    }

    @Test void snapshotRefreshWaitsForBusinessCommitAndReadsLatestScore() throws Exception {
        behavior.recordBehavior(1L, LearnBehaviorType.STUDY, 60, "first-score");
        drain();
        double old = fixtures.selectScore();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        java.util.concurrent.atomic.AtomicReference<Future<?>> refresh = new java.util.concurrent.atomic.AtomicReference<>();
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                behavior.recordBehavior(1L, LearnBehaviorType.STUDY, 540, "new-score");
                CountDownLatch started = new CountDownLatch(1);
                refresh.set(pool.submit(() -> { started.countDown(); snapshots.refreshUserCourseScore(2L,1L); }));
                try {
                    assertTrue(started.await(5,TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> refresh.get().get(150,TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            });
            refresh.get().get(10,TimeUnit.SECONDS);
            double latest = fixtures.selectScore();
            assertTrue(latest > old);
        } finally { pool.shutdownNow(); }
    }

    @Test void backgroundProcessorDrainsDependenciesAndPublishesMetrics() {
        behavior.recordBehavior(1L, LearnBehaviorType.STUDY, 600, "processor-event");
        var registry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
        var properties = new LearningOutboxProperties(true,4,60,20,20,5,300);
        var processor = new LearningOutboxProcessor(store,handler,properties,registry);
        try {
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).untilAsserted(() -> {
                processor.scan();
                assertEquals(4, store.count("DONE"));
            });
            assertTrue(registry.get("learning.outbox.completed").tag("type","HOT_INCREMENT").counter().count() >= 1);
        } finally { processor.close(); registry.close(); }
    }

    private void expire(LearningOutboxTask task) {
        fixtures.expireTask(task.id());
    }
    private int seconds() { return fixtures.selectLearnedSeconds(); }
    private LearningOutboxTask claimType(String type) throws Exception {
        // 先完成其他可执行任务；依赖测试可确定拿到所需目标。
        for (int i=0; i<10; i++) {
            LearningOutboxTask task = store.claim();
            assertNotNull(task);
            if (task.type().equals(type)) return task;
            store.complete(task, handler.handle(task));
        }
        throw new AssertionError("未找到任务 " + type);
    }
    private void drain() throws Exception {
        for (int i=0; i<30; i++) {
            var task = store.claim();
            if (task == null) return;
            store.complete(task, handler.handle(task));
        }
        fail("任务未收敛");
    }
    private void concurrently(int count, Callable<?> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        try {
            List<Future<?>> futures = new ArrayList<>();
            CountDownLatch start = new CountDownLatch(1);
            for (int i=0; i<count; i++) futures.add(pool.submit(() -> {
                start.await();
                UserContext.set(new UserInfo(2L,"test","USER"));
                try { return action.call(); } finally { UserContext.clear(); }
            }));
            start.countDown();
            for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }
}
