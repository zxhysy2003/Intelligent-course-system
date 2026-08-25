package com.sy.course_system.experiment;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.sy.course_system.entity.LearningBehavior;
import com.sy.course_system.mapper.LearningBehaviorMapper;

@TestConfiguration(proxyBeanMethods = false)
public class StudyInsertFaultInjectionConfiguration {

    static final String FAILURE_MESSAGE = "CON-03 故障注入：STUDY 事件日志插入后、事务提交前";

    @Bean
    static BeanPostProcessor studyInsertFaultInjectionBeanPostProcessor(
            @Value("${CON03_FAIL_AFTER_STUDY_INSERT_ENABLED:false}") boolean enabled,
            @Value("${CON03_FAIL_AFTER_STUDY_INSERT_EVENT_ID:}") String targetEventId) {
        return new StudyInsertFaultInjectionBeanPostProcessor(enabled, targetEventId);
    }

    private static final class StudyInsertFaultInjectionBeanPostProcessor implements BeanPostProcessor {

        private final boolean enabled;
        private final String targetEventId;
        private final AtomicBoolean faultConsumed = new AtomicBoolean(false);

        private StudyInsertFaultInjectionBeanPostProcessor(boolean enabled, String targetEventId) {
            this.enabled = enabled;
            this.targetEventId = targetEventId;
        }

        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName) {
            if (!(bean instanceof LearningBehaviorMapper)) {
                return bean;
            }

            ProxyFactory proxyFactory = new ProxyFactory();
            proxyFactory.setTarget(bean);
            proxyFactory.setInterfaces(LearningBehaviorMapper.class);
            proxyFactory.addAdvice((MethodInterceptor) invocation -> {
                Object result = invocation.proceed();
                injectFailureAfterSuccessfulInsert(invocation.getMethod().getName(), invocation.getArguments(), result);
                return result;
            });
            return proxyFactory.getProxy(bean.getClass().getClassLoader());
        }

        private void injectFailureAfterSuccessfulInsert(String methodName, Object[] arguments, Object result) {
            if (!enabled || !"insertStudyIfAbsent".equals(methodName)) {
                return;
            }
            if (!(result instanceof Number inserted) || inserted.intValue() != 1) {
                return;
            }
            if (arguments.length != 1 || !(arguments[0] instanceof LearningBehavior behavior)) {
                return;
            }
            if (!Objects.equals(targetEventId, behavior.getEventId())) {
                return;
            }
            if (faultConsumed.compareAndSet(false, true)) {
                throw new IllegalStateException(FAILURE_MESSAGE);
            }
        }
    }
}
