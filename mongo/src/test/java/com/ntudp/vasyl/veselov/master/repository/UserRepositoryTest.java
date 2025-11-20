package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.MongoUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MongoDBContainer;
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
    private static final MongoDBContainer MONGO_CONTAINER =
            new MongoDBContainer("mongo:latest");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_CONTAINER::getConnectionString);
        registry.add("spring.data.mongodb.database", () -> "test");
    }

    @Test
    void test() throws Exception {
        Random rand = new Random();
        List<MongoUser> users = new ArrayList<>();
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
                MongoUser user = new MongoUser();


                if (users.size() > 2) {
                    user.getFriends().add(users.get(rand.nextInt(users.size() - 1)));
                }

                users.add(userRepository.save(user));
            }
        });

        synchronized (monitor) {
            monitor.wait();
        }

        log.info("All users created. Total: {}", userRepository.findAll().size());
        log.info("Users with friends:{}", userRepository.findAll().stream()
                .filter(user -> !user.getFriends().isEmpty())
                .count());
    }
}