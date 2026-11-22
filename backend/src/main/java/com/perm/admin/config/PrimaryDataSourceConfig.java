package com.perm.admin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * 把第一个 autoInit 数据源（或内存 H2 兜底）注册为 Spring 主 DataSource，
 * 供 MyBatis-Plus / JdbcTemplate 等自动配置使用。
 */
@Configuration
public class PrimaryDataSourceConfig {

    @Bean
    @Primary
    public DataSource primaryDataSource(DataSourceManager dataSourceManager) {
        return dataSourceManager.primary();
    }
}
