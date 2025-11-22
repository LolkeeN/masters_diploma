package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.CassandraUser;
import com.ntudp.vasyl.veselov.master.dto.FriendInfo;
import com.ntudp.vasyl.veselov.master.util.CsvUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.TestPropertySources;
import org.testcontainers.cassandra.CassandraContainer;
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
    private static final CassandraContainer CASSANDRA_CONTAINER =
            new CassandraContainer("cassandra:latest")
                    .withInitScript(
                            "com/ntudp/vasyl/veselov/master/repository/init-cassandra.cql"
                    )
                    .withSharedMemorySize(8_000_000_000L)
                    .withEnv("HEAP_NEWSIZE", "500m")
                    .withEnv("MAX_HEAP_SIZE", "2g")
            ;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        // todo add cassandra properties
//        registry.add("spring.datasource.url", CASSANDRA_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", CASSANDRA_CONTAINER::getUsername);
        registry.add("spring.datasource.password", CASSANDRA_CONTAINER::getPassword);
    }

    @Test
    @Timeout(value = 3600, unit = TimeUnit.MINUTES)
    void test() throws Exception {
        Random rand = new Random();
        List<CassandraUser> users = new ArrayList<>();
        AtomicInteger atomicInteger = new AtomicInteger();
        List<String[]> dataLines = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        dataLines.add(new String[] {
                "Operation", "Time (ms)"
        });

        long start = System.currentTimeMillis();
        List<CassandraUser> finalUsers = users;
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
                CassandraUser user = new CassandraUser();

                if (finalUsers.size() > 2) {
                    CassandraUser friendUser = finalUsers.get(rand.nextInt(finalUsers.size() - 1));
                    user.getFriends().add(new FriendInfo(friendUser));
                }

                finalUsers.add(userRepository.save(user));
            }
        });

        synchronized (monitor) {
            monitor.wait();
        }

        CassandraUser randomUser = users.get(rand.nextInt(users.size() - 1));
        dataLines.add(new String[] {
                "Generate starting users", String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("Find all and interact with many to many relation");
        start = System.currentTimeMillis();
        userRepository.findAll()
                .stream()
                .filter(u -> u.getFriends() != null && u.getFriends().size() > 1)
                .count();

        dataLines.add(new String[] {
                "Find all and interact with many to many relation", String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("Find by id");
        start = System.currentTimeMillis();
        userRepository.findById(randomUser.getId()); // Явно используем String ID
        dataLines.add(new String[] {
                "Find by id", String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("delete by id");
        start = System.currentTimeMillis();
        userRepository.deleteById(randomUser.getId());
        dataLines.add(new String[] {
                "Delete by id with all friendships", String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        log.info("Count all users");
        start = System.currentTimeMillis();
        userRepository.count();
        dataLines.add(new String[] {
                "Count all", String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("Update user");
        start = System.currentTimeMillis();
        randomUser = users.get(rand.nextInt(users.size() - 1));
        randomUser.setEmail(UUID.randomUUID().toString());
        userRepository.save(randomUser);
        dataLines.add(new String[] {
                "Update user", String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        log.info("Find 10 percent users");
        int tenPercent = usersCount / 10;
        Set<CassandraUser> tenPercentUsers = users.stream()
                .limit(tenPercent)
                .collect(Collectors.toSet());

        start = System.currentTimeMillis();
        userRepository.findAllById(tenPercentUsers.stream().map(CassandraUser::getId).collect(Collectors.toList()));

        dataLines.add(new String[] {
                "Find %s(10 percent) random users".formatted(tenPercent), String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("update 10 percent users");

        List<CassandraUser> updatedTenPercent = tenPercentUsers.stream()
                .map(this::updateAddress)
                .toList();
        start = System.currentTimeMillis();
        userRepository.saveAll(updatedTenPercent);
        dataLines.add(new String[] {
                "Update address for %s(10 percent) random users".formatted(tenPercent), String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        log.info("find 50 percent users");

        int fiftyPercent = usersCount / 2;
        Set<CassandraUser> fiftyPercentUsers = users.stream()
                .limit(fiftyPercent)
                .collect(Collectors.toSet());

        start = System.currentTimeMillis();
        userRepository.findAllById(fiftyPercentUsers.stream().map(CassandraUser::getId).collect(Collectors.toList()));

        dataLines.add(new String[] {
                "Find %s(50 percent) random users".formatted(fiftyPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("update 50 percent users");

        List<CassandraUser> updated50Percent = fiftyPercentUsers.stream()
                .map(this::updateAddress)
                .toList();
        start = System.currentTimeMillis();
        userRepository.saveAll(updated50Percent);
        dataLines.add(new String[] {
                "Update address for %s(50 percent) random users".formatted(fiftyPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        log.info("find 15 percent users");

        int fifteenPercent = (usersCount * 15) / 100;
        Set<CassandraUser> fifteenPercentUsers = users.stream()
                .limit(fifteenPercent)
                .collect(Collectors.toSet());

        log.info("delete 15 percent users with friends");

        start = System.currentTimeMillis();
        List<String> idsToDelete = fifteenPercentUsers.stream()
                .map(CassandraUser::getId)
                .collect(Collectors.toList());
        fifteenPercentUsers
                .stream()
                .filter(user -> user.getFriends() != null && !user.getFriends().isEmpty())
                .flatMap(user -> user.getFriends().stream().map(FriendInfo::getId))
                .forEach(idsToDelete::add);
        idsToDelete.forEach(userRepository::deleteById);
        dataLines.add(new String[]{
                "Delete users with friends for %s(15 percent) random users".formatted(fifteenPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("delete all users");
        start = System.currentTimeMillis();
        userRepository.deleteAll();
        dataLines.add(new String[] {
                "Delete all users", String.valueOf(System.currentTimeMillis() - start)
        });

        CsvUtil.generateFile(usersCount + "_users_cassandra_statistics", dataLines);
    }


    private CassandraUser updateAddress(CassandraUser user) {
        user.setAddress(UUID.randomUUID().toString());
        return user;
    }
}