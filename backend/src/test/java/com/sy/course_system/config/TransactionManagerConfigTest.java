package com.sy.course_system.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.util.Set;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Driver;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class TransactionManagerConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(Driver.class, () -> mock(Driver.class))
            .withBean(DatabaseSelectionProvider.class, DatabaseSelectionProvider::getDefaultSelectionProvider)
            .withUserConfiguration(TransactionManagerConfig.class);

    @Test
    void shouldRegisterJdbcAsDefaultAndKeepNeo4jTransactionManagerSeparate() {
        contextRunner.run(context -> {
            PlatformTransactionManager jdbcTransactionManager = context.getBean(
                    "transactionManager", PlatformTransactionManager.class);
            PlatformTransactionManager neo4jTransactionManager = context.getBean(
                    "neo4jTransactionManager", PlatformTransactionManager.class);

            assertInstanceOf(JdbcTransactionManager.class, jdbcTransactionManager);
            assertInstanceOf(Neo4jTransactionManager.class, neo4jTransactionManager);
            assertSame(jdbcTransactionManager, context.getBean("jdbcTransactionManager"));
            assertSame(jdbcTransactionManager, context.getBean(PlatformTransactionManager.class));
            assertEquals(Set.of("transactionManager", "neo4jTransactionManager"),
                    context.getBeansOfType(PlatformTransactionManager.class).keySet());
        });
    }

    @Test
    void defaultTransactionManagerShouldRollBackJdbcConnectionWhenBusinessFails() {
        contextRunner.run(context -> {
            DataSource dataSource = context.getBean(DataSource.class);
            Connection connection = mock(Connection.class);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.getAutoCommit()).thenReturn(true);

            TransactionTemplate transactionTemplate = new TransactionTemplate(
                    context.getBean("transactionManager", PlatformTransactionManager.class));

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    () -> transactionTemplate.executeWithoutResult(status -> {
                        throw new IllegalStateException("模拟 MySQL 业务写入后失败");
                    }));

            assertEquals("模拟 MySQL 业务写入后失败", exception.getMessage());
            verify(connection).setAutoCommit(false);
            verify(connection).rollback();
            verify(connection, never()).commit();
            verify(connection).setAutoCommit(true);
            verify(connection).close();
        });
    }

    @Test
    void neo4jRepositoriesShouldUseDedicatedTransactionManager() {
        EnableNeo4jRepositories annotation = Neo4jConfig.class.getAnnotation(EnableNeo4jRepositories.class);

        assertNotNull(annotation);
        assertEquals("neo4jTransactionManager", annotation.transactionManagerRef());
    }
}
