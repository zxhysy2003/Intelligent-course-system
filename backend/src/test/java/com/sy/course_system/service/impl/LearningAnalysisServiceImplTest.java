package com.sy.course_system.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.mapper.UserCourseRelationMapper;
import com.sy.course_system.repository.KnowledgeRepository;

@ExtendWith(MockitoExtension.class)
class LearningAnalysisServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ZSetOperations<String, Object> zSetOperations;
    @Mock
    private LearningBehaviorMapper learningBehaviorMapper;
    @Mock
    private UserCourseRelationMapper userCourseRelationMapper;
    @Mock
    private KnowledgeRepository knowledgeRepository;

    private LearningAnalysisServiceImpl learningAnalysisService;

    @BeforeEach
    void setUp() {
        learningAnalysisService = new LearningAnalysisServiceImpl(redisTemplate, learningBehaviorMapper,
                userCourseRelationMapper, knowledgeRepository);
    }

    @Test
    void increaseCourseHotShouldIncrementRedisHotScore() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        learningAnalysisService.increaseCourseHot(10L, 1.5d);

        verify(zSetOperations).incrementScore("course:hot", 10L, 1.5d);
    }
}
