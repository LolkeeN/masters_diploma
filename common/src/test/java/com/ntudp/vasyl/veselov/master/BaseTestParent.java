package com.ntudp.vasyl.veselov.master;

import java.time.Duration;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.ComponentScan;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.junit.jupiter.Container;

@SpringBootTest
@EnableConfigurationProperties
@ComponentScan(basePackages = "com.ntudp")
@Execution(ExecutionMode.SAME_THREAD)
public class BaseTestParent {

    @ServiceConnection("postgres")
    @Container
    PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>("postgres:latest")
            .withExposedPorts(5432);

    @ServiceConnection("redis")
    @SuppressWarnings("resource")
    @Container
    public GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>("redis:latest").withExposedPorts(6379);

    @ServiceConnection("h2")
    @SuppressWarnings("resource")
    @Container
    public GenericContainer<?> H2_CONTAINER =
            new GenericContainer<>("oscarfonts/h2:latest").withExposedPorts(6379);

    @ServiceConnection("mysql")
    @SuppressWarnings("resource")
    @Container
    public MySQLContainer<?> MYSQL_CONTAINER =
            new MySQLContainer<>("mysql:latest");

    @ServiceConnection("mysql")
    @SuppressWarnings("resource")
    @Container
    public CassandraContainer CASSANDRA_CONTAINER =
            new CassandraContainer("cassandra:latest");

    @ServiceConnection("msSql")
    @SuppressWarnings("resource")
    @Container
    public MSSQLServerContainer<?> MS_SQL_CONTAINER =
            new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
                    .acceptLicense()
                    .withPassword("yourStrong(!)Password")
                    .withEnv("MSSQL_AGENT_ENABLED", "true")
                    .withStartupCheckStrategy(new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(5)))
                    .withReuse(true);
}
