package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.CassandraUser;
import com.ntudp.vasyl.veselov.master.dto.FriendInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Slf4j
@Testcontainers
@SpringBootTest
@TestPropertySource("classpath:application-test.properties")
class UserRepositoryTest {

    private final Object monitor = new Object();
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
    void test() throws Exception {
        Random rand = new Random();
        List<CassandraUser> users = new ArrayList<>();
        AtomicInteger atomicInteger = new AtomicInteger();
        Executors.newFixedThreadPool(15).execute(() -> {
            while (true) {
                int i = atomicInteger.incrementAndGet();
                if ((i > 5000)) {
                    synchronized (monitor) {
                        monitor.notify();
                    }
                    break;
                }

                log.info("User created: {}", i);
                CassandraUser user = new CassandraUser();


                if (users.size() > 2) {
                    if (user.getFriends() == null) {
                        user.setFriends(new HashSet<>());
                    }
                    user.getFriends().add(new FriendInfo(users.get(rand.nextInt(users.size() - 1))));
                } else {
                    user.setFriends(new HashSet<>());
                }

                users.add(userRepository.save(user));
            }
        });

        synchronized (monitor) {
            monitor.wait();
        }

        log.info("All users created. Total: {}", userRepository.findAll().size());
        log.info("Users with friends:{}", userRepository.findAll().stream()
                .filter(user -> user.getFriends() != null && !user.getFriends().isEmpty())
                .count());


    }

}