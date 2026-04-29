package com.sy.course_system.service.impl;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import com.sy.course_system.mapper.LearningBehaviorMapper;
import com.sy.course_system.mapper.UserCourseRelationMapper;
import com.sy.course_system.repository.KnowledgeRepository;

@ExtendWith(MockitoExtension.class)
class LearningAnalysisServiceImplTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private LearningBehaviorMapper learningBehaviorMapper;
    @Mock
    private UserCourseRelationMapper userCourseRelationMapper;
    @Mock
    private KnowledgeRepository knowledgeRepository;

    @InjectMocks
    private LearningAnalysisServiceImpl learningAnalysisService;

    @Test
    void refreshUserRecommendCacheShouldDeleteUserResultAndScoreMatrix() {
        learningAnalysisService.refreshUserRecommendCache(1L);

        verify(redisTemplate).delete("recommend:user:1");
        verify(redisTemplate).delete("recommend:score-matrix");
    }
}
