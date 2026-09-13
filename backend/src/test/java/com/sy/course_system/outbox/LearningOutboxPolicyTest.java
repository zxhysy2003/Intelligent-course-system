package com.sy.course_system.outbox;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import com.sy.course_system.mapper.LearningOutboxMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class LearningOutboxPolicyTest {
    @Test void retryDelayIsBoundedAndConfigurationRejectsUnsafeLease() {
        var properties = new LearningOutboxProperties(true,4,60,20,20,5,300);
        var store = new LearningOutboxStore(mock(LearningOutboxMapper.class),properties);
        assertEquals(5, store.retryDelay(1));
        for (int attempts=1; attempts<=100; attempts++) {
            long delay=store.retryDelay(attempts);
            assertTrue(delay>=5 && delay<=300);
        }
        assertThrows(IllegalArgumentException.class, () -> new LearningOutboxProperties(true,4,30,20,20,5,300));
    }

    @Test void pausedProcessorOnlyObservesWithoutClaiming() {
        var store=mock(LearningOutboxStore.class);
        var handler=mock(LearningOutboxHandler.class);
        var metrics=new SimpleMeterRegistry();
        var processor=new LearningOutboxProcessor(store,handler,
                new LearningOutboxProperties(false,4,60,20,20,5,300),metrics);
        try {
            processor.scan();
            verify(store,never()).claim();
            verifyNoInteractions(handler);
        } finally { processor.close(); metrics.close(); }
    }
}
