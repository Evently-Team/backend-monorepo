package com.evently.user.datasource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Configuration class for setting up master and slave data sources.
 * - Read queries executed in function block with {@code @SlaveTransactional}
 *   are routed to the slave data source, which can include multiple PostgreSQL DB nodes
 *   using a proxy like HAPRoxy for load balancing TCP requests.
 * - Write queries with just {@code @Transactional} are routed to the master data source.
 */
@Configuration
public class MasterSlaveDataSourcesConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "spring.master.datasource")
    public DataSource masterDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    @ConfigurationProperties(prefix = "spring.slave.datasource")
    public DataSource slaveDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean
    public DataSource routingDataSource(DataSource masterDataSource, DataSource slaveDataSource) {
        final MasterSlaveRoutingDataSource routingDataSource = new MasterSlaveRoutingDataSource();

        final Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put("masterDataSource", masterDataSource);
        dataSourceMap.put("slaveDataSource", slaveDataSource);

        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(masterDataSource);

        return routingDataSource;
    }

    @Bean
    @Primary
    public DataSource dataSource() {
        return routingDataSource(masterDataSource(), slaveDataSource());
    }

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        final LocalContainerEntityManagerFactoryBean entityManagerFactory = new LocalContainerEntityManagerFactoryBean();
        entityManagerFactory.setDataSource(dataSource());
        entityManagerFactory.setPackagesToScan("com.evently.user.entity.persistent");

        final JpaVendorAdapter jpaVendorAdapter = new HibernateJpaVendorAdapter();
        entityManagerFactory.setJpaVendorAdapter(jpaVendorAdapter);

        final Properties properties = new Properties();
        properties.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

        entityManagerFactory.setJpaProperties(properties);

        return entityManagerFactory;
    }
}
