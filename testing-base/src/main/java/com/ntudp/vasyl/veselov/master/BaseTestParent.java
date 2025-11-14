package com.ntudp.vasyl.veselov.master;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.ComponentScan;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.ntudp.vasyl.veselov.master.InfrastructureDependencies.CASSANDRA_CONTAINER;
import static com.ntudp.vasyl.veselov.master.InfrastructureDependencies.H2_CONTAINER;
import static com.ntudp.vasyl.veselov.master.InfrastructureDependencies.MS_SQL_CONTAINER;
import static com.ntudp.vasyl.veselov.master.InfrastructureDependencies.MYSQL_CONTAINER;
import static com.ntudp.vasyl.veselov.master.InfrastructureDependencies.REDIS_CONTAINER;
import static com.ntudp.vasyl.veselov.master.InfrastructureDependencies.POSTGRES_CONTAINER;

@Testcontainers
@ImportTestcontainers(InfrastructureDependencies.class)
@SpringBootTest
@EnableConfigurationProperties
@ComponentScan(basePackages = "com.ntudp")
@Execution(ExecutionMode.SAME_THREAD)
public abstract class BaseTestParent {

    static {
        POSTGRES_CONTAINER.start();
        REDIS_CONTAINER.start();
        H2_CONTAINER.start();
        MYSQL_CONTAINER.start();
        CASSANDRA_CONTAINER.start();
        MS_SQL_CONTAINER.start();
    }
}
