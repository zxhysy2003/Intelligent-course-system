package com.sy.course_system.config;

import javax.sql.DataSource;

import org.neo4j.driver.Driver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.jdbc.support.JdbcTransactionManager;

@Configuration(proxyBeanMethods = false)
public class TransactionManagerConfig {

    /**
     * 保留现有 transactionManager 名称，确保 MyBatis/MySQL 业务事务绑定 JDBC 连接。
     */
    @Bean(name = { "transactionManager", "jdbcTransactionManager" })
    @Primary
    public JdbcTransactionManager jdbcTransactionManager(DataSource dataSource) {
        return new JdbcTransactionManager(dataSource);
    }

    /**
     * Neo4j 仓储显式使用独立事务管理器，避免与 MySQL 事务混淆。
     */
    @Bean("neo4jTransactionManager")
    public Neo4jTransactionManager neo4jTransactionManager(
            Driver driver,
            DatabaseSelectionProvider databaseSelectionProvider) {
        return new Neo4jTransactionManager(driver, databaseSelectionProvider);
    }
}
