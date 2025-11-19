package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.CassandraUser;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@TestPropertySource("classpath:application-test.properties")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @ServiceConnection
    @Container
    private static final CassandraContainer CASSANDRA_CONTAINER =
            new CassandraContainer("cassandra:latest")
                    .withInitScript(
                            "com/ntudp/vasyl/veselov/master/repository/init-cassandra.cql"
                    );

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        // todo add cassandra properties
//        registry.add("spring.datasource.url", CASSANDRA_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", CASSANDRA_CONTAINER::getUsername);
        registry.add("spring.datasource.password", CASSANDRA_CONTAINER::getPassword);
    }

    @Test
    void test() {
        userRepository.save(new CassandraUser());
    }
}