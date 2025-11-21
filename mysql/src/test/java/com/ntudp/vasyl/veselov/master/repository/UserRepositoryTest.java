package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.SqlUser;
import com.ntudp.vasyl.veselov.master.dto.User;
import com.ntudp.vasyl.veselov.master.util.CsvUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@Testcontainers
@SpringBootTest
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
    private static final MySQLContainer<?> MYSQL_CONTAINER =
            new MySQLContainer<>("mysql:8.1.0")
                    .withDatabaseName("uzer")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.hibernate.default_schema", () -> "uzer");
    }

    @Test
    void test() throws Exception {
        Random rand = new Random();
        List<SqlUser> users = new ArrayList<>();
        AtomicInteger atomicInteger = new AtomicInteger();
        List<String[]> dataLines = new ArrayList<>();
        dataLines.add(new String[] {
                "Operation", "Time (ms)"
        });

        long start = System.currentTimeMillis();
        Executors.newFixedThreadPool(15).execute(() -> {
            while (true) {
                int i = atomicInteger.incrementAndGet();
                if ((i > usersCount)) {
                    synchronized (monitor) {
                        monitor.notify();
                    }
                    break;
                }

                log.info("User created: {}", i);
                SqlUser user = new SqlUser();


                if (users.size() > 2) {
                    user.getFriends().add(users.get(rand.nextInt(users.size() - 1)));
                }

                users.add(userRepository.save(user));
            }
        });

        synchronized (monitor) {
            monitor.wait();
        }

        Set<String> usedIds = new HashSet<>();
        dataLines.add(new String[] {
                "Generate starting users", String.valueOf(System.currentTimeMillis() - start)
        });


        SqlUser randomUser = users.get(rand.nextInt(users.size() - 1));
        start = System.currentTimeMillis();
        userRepository.findAll()
                .stream()
                .filter(u -> u.getFriends().size() > 1)
                .count();

        dataLines.add(new String[] {
                "Find all and interact with many to many relation", String.valueOf(System.currentTimeMillis() - start)
        });

        start = System.currentTimeMillis();
        userRepository.findById(randomUser.getId());
        dataLines.add(new String[] {
                "Find by id", String.valueOf(System.currentTimeMillis() - start)
        });
        usedIds.add(randomUser.getId());

        randomUser = users.get(rand.nextInt(users.size() - 1));
        start = System.currentTimeMillis();
        userRepository.deleteUserWithFriendships(randomUser.getId());
        dataLines.add(new String[] {
                "Delete by id", String.valueOf(System.currentTimeMillis() - start)
        });
        users.remove(randomUser);
        usedIds.add(randomUser.getId());

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
        usedIds.add(randomUser.getId());

        int tenPercent = usersCount / 10;
        Set<SqlUser> tenPercentUsers = users.stream()
                .filter(user -> !usedIds.contains(user.getId()))
                .limit(tenPercent)
                .collect(Collectors.toSet());

        start = System.currentTimeMillis();
        userRepository.findAllById(tenPercentUsers.stream().map(SqlUser::getId).collect(Collectors.toList()));

        dataLines.add(new String[] {
                "Find %s(10 percent) random users".formatted(tenPercent), String.valueOf(System.currentTimeMillis() - start)
        });

        List<SqlUser> updatedTenPercent = tenPercentUsers.stream()
                .peek(this::updateEmail)
                .toList();
        start = System.currentTimeMillis();
        userRepository.saveAll(updatedTenPercent);
        dataLines.add(new String[] {
                "Update email for %s(10 percent) random users".formatted(tenPercent), String.valueOf(System.currentTimeMillis() - start)
        });
        usedIds.addAll(tenPercentUsers.stream().map(SqlUser::getId).toList());

        int fiftyPercent = usersCount / 2;
        Set<SqlUser> fiftyPercentUsers = users.stream()
                .filter(user -> !usedIds.contains(user.getId()))
                .limit(tenPercent)
                .collect(Collectors.toSet());

        start = System.currentTimeMillis();
        userRepository.findAllById(fiftyPercentUsers.stream().map(SqlUser::getId).collect(Collectors.toList()));

        dataLines.add(new String[] {
                "Find %s(10 percent) random users".formatted(fiftyPercentUsers), String.valueOf(System.currentTimeMillis() - start)
        });

        List<SqlUser> updated50Percent = fiftyPercentUsers.stream()
                .peek(this::updateEmail)
                .toList();
        start = System.currentTimeMillis();
        userRepository.saveAll(updated50Percent);
        dataLines.add(new String[] {
                "Update email for %s(50 percent) random users".formatted(fiftyPercent), String.valueOf(System.currentTimeMillis() - start)
        });
        usedIds.addAll(fiftyPercentUsers.stream().map(SqlUser::getId).toList());

        start = System.currentTimeMillis();
        userRepository.deleteAll();
        dataLines.add(new String[] {
                "Delete all users", String.valueOf(System.currentTimeMillis() - start)
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

        CsvUtil.generateFile("mysql_statistics", dataLines);
    }

    private User updateEmail(SqlUser user) {
        user.setAddress(UUID.randomUUID().toString());
        return user;
    }

}