package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.CassandraUser;
import com.ntudp.vasyl.veselov.master.dto.FriendInfo;
import com.ntudp.vasyl.veselov.master.util.CsvUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
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
        List<String[]> dataLines = new ArrayList<>();
        dataLines.add(new String[] {
                "Operation", "Time (ms)"
        });

        long start = System.currentTimeMillis();
        Executors.newFixedThreadPool(15).execute(() -> {
            while (true) {
                int i = atomicInteger.incrementAndGet();
                if ((i > 100)) {
                    synchronized (monitor) {
                        monitor.notify();
                    }
                    break;
                }

                log.info("User created: {}", i);
                CassandraUser user = new CassandraUser();

                if (users.size() > 2) {
                    CassandraUser friendUser = users.get(rand.nextInt(users.size() - 1));
                    user.getFriends().add(new FriendInfo(friendUser));
                }

                users.add(userRepository.save(user));
            }
        });

        synchronized (monitor) {
            monitor.wait();
        }

        dataLines.add(new String[] {
                "Generate starting users", String.valueOf(System.currentTimeMillis() - start)
        });

        start = System.currentTimeMillis();
        userRepository.findAll()
                .stream()
                .filter(u -> u.getFriends() != null && u.getFriends().size() > 1)
                .count();

        dataLines.add(new String[] {
                "Find all and interact with many to many relation", String.valueOf(System.currentTimeMillis() - start)
        });

        start = System.currentTimeMillis();
        String randomUserId = users.get(rand.nextInt(users.size() - 1)).getId();
        userRepository.findById(randomUserId); // Явно используем String ID
        dataLines.add(new String[] {
                "Find by id", String.valueOf(System.currentTimeMillis() - start)
        });

        start = System.currentTimeMillis();
        CassandraUser randomUser = users.get(rand.nextInt(users.size() - 1));
        userRepository.deleteById(randomUser.getId());
        dataLines.add(new String[] {
                "Delete by id", String.valueOf(System.currentTimeMillis() - start)
        });
        users.remove(randomUser);

        start = System.currentTimeMillis();
        userRepository.count();
        dataLines.add(new String[] {
                "Count all", String.valueOf(System.currentTimeMillis() - start)
        });

        start = System.currentTimeMillis();
        randomUser = users.get(rand.nextInt(users.size() - 1));
        randomUser.setEmail(UUID.randomUUID().toString());
        userRepository.save(randomUser);
        dataLines.add(new String[] {
                "Update user", String.valueOf(System.currentTimeMillis() - start)
        });

        CsvUtil.generateFile("cassandra_statistics", dataLines);
    }


}