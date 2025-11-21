package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.MongoUser;
import com.ntudp.vasyl.veselov.master.util.CsvUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.TestPropertySources;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Slf4j
@Testcontainers
@SpringBootTest
@TestPropertySource("classpath:application-test.properties")
@TestPropertySources({
        @TestPropertySource("classpath:application-test.properties"),
        @TestPropertySource("classpath:test-common.properties")
})
class UserRepositoryTest {

    private final Object monitor = new Object();
    @Autowired
    private UserRepository userRepository;

    @Value("${test.users.count}")
    private int usersCount;

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

        dataLines.add(new String[] {
                "Generate starting users", String.valueOf(System.currentTimeMillis() - start)
        });


        start = System.currentTimeMillis();
        userRepository.findAll()
                .stream()
                .filter(u -> u.getFriends().size() > 1)
                .count();

        dataLines.add(new String[] {
                "Find all and interact with many to many relation", String.valueOf(System.currentTimeMillis() - start)
        });

        start = System.currentTimeMillis();
        userRepository.findById(users.get(rand.nextInt(users.size() - 1)).getId());
        dataLines.add(new String[] {
                "Find by id", String.valueOf(System.currentTimeMillis() - start)
        });

        start = System.currentTimeMillis();
        MongoUser randomUser = users.get(rand.nextInt(users.size() - 1));
        userRepository.deleteById(randomUser.getId());
        dataLines.add(new String[] {
                "Delete by id with all friendships", String.valueOf(System.currentTimeMillis() - start)
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
//
//        start = System.currentTimeMillis();
//        userRepository.findByEmail(randomUser.getEmail());
//        dataLines.add(new String[] {
//                "Find by email", String.valueOf(System.currentTimeMillis() - start)
//        });
//
//        start = System.currentTimeMillis();
//        userRepository.findByFriendsId(randomUser.getId());
//        dataLines.add(new String[] {
//                "Find by friends id", String.valueOf(System.currentTimeMillis() - start)
//        });

        CsvUtil.generateFile("mongo_statistics", dataLines);
    }
}