package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.RedisUser;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
    private static final GenericContainer<?> REDIS_CONTAINER =
            new GenericContainer<>("redis:latest")
                    .withExposedPorts(6379)
                    .withSharedMemorySize(2_000_000_000L)  // 2GB
                    .withCommand("redis-server", "--maxmemory", "1gb", "--maxmemory-policy", "allkeys-lru")
            ;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", REDIS_CONTAINER::getHost);
        registry.add("spring.redis.port", () -> REDIS_CONTAINER.getExposedPorts().getFirst());
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.MINUTES)
    void test() throws Exception {
        Random rand = new Random();
        List<RedisUser> users = new ArrayList<>();
        AtomicInteger atomicInteger = new AtomicInteger();
        List<String[]> dataLines = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        dataLines.add(new String[]{
                "Operation", "Time (ms)"
        });

        long start = System.currentTimeMillis();
        List<RedisUser> finalUsers = users;
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
                RedisUser user = new RedisUser();

                if (finalUsers.size() > 2) {
                    user.getFriends().add(finalUsers.get(rand.nextInt(finalUsers.size() - 1)));
                }

                finalUsers.add(userRepository.save(user));
            }
        });

        synchronized (monitor) {
            monitor.wait();
        }

        RedisUser randomUser = users.get(rand.nextInt(users.size() - 1));
        dataLines.add(new String[]{
                "Generate starting users", String.valueOf(System.currentTimeMillis() - start)
        });

        start = System.currentTimeMillis();
        userRepository.findAll()
                .stream()
                .filter(u -> u.getFriends().size() > 1)
                .count();

        dataLines.add(new String[]{
                "Find all and interact with many to many relation", String.valueOf(System.currentTimeMillis() - start)
        });

        start = System.currentTimeMillis();
        userRepository.findById(randomUser.getId());
        dataLines.add(new String[]{
                "Find by id", String.valueOf(System.currentTimeMillis() - start)
        });

        users = userRepository.findAll();
        randomUser = users.get(rand.nextInt(users.size() - 1));

        start = System.currentTimeMillis();
        userRepository.deleteById(randomUser.getId());
        dataLines.add(new String[]{
                "Delete by id with all friendships", String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();


        start = System.currentTimeMillis();
        userRepository.count();
        dataLines.add(new String[]{
                "Count all", String.valueOf(System.currentTimeMillis() - start)
        });

        start = System.currentTimeMillis();
        randomUser = users.get(rand.nextInt(users.size() - 1));
        randomUser.setEmail(UUID.randomUUID().toString());
        userRepository.save(randomUser);
        dataLines.add(new String[]{
                "Update user", String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        int tenPercent = usersCount / 10;
        Set<RedisUser> tenPercentUsers = users.stream()
                .limit(tenPercent)
                .collect(Collectors.toSet());

        start = System.currentTimeMillis();
        userRepository.findAllById(tenPercentUsers.stream().map(RedisUser::getId).collect(Collectors.toList()));

        dataLines.add(new String[]{
                "Find %s(10 percent) random users".formatted(tenPercent), String.valueOf(System.currentTimeMillis() - start)
        });

        List<RedisUser> updatedTenPercent = tenPercentUsers.stream()
                .map(this::updateAddress)
                .toList();
        start = System.currentTimeMillis();
        userRepository.saveAll(updatedTenPercent);
        dataLines.add(new String[]{
                "Update address for %s(10 percent) random users".formatted(tenPercent), String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        int fiftyPercent = usersCount / 2;
        Set<RedisUser> fiftyPercentUsers = users.stream()
                .limit(fiftyPercent)
                .collect(Collectors.toSet());

        start = System.currentTimeMillis();
        userRepository.findAllById(fiftyPercentUsers.stream().map(RedisUser::getId).collect(Collectors.toList()));

        dataLines.add(new String[]{
                "Find %s(50 percent) random users".formatted(fiftyPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });

        List<RedisUser> updated50Percent = fiftyPercentUsers.stream()
                .map(this::updateAddress)
                .toList();
        start = System.currentTimeMillis();
        userRepository.saveAll(updated50Percent);
        dataLines.add(new String[]{
                "Update address for %s(50 percent) random users".formatted(fiftyPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });

        start = System.currentTimeMillis();
        userRepository.deleteAll();
        dataLines.add(new String[]{
                "Delete all users", String.valueOf(System.currentTimeMillis() - start)
        });

        CsvUtil.generateFile(usersCount + "_users_redis_statistics", dataLines);
    }

    private static void setUsedForUserAndHisFriends(Set<String> usedIds, Set<RedisUser> setOfUsers) {
        usedIds.addAll(setOfUsers.stream().map(RedisUser::getId).toList());
        usedIds.addAll(setOfUsers.stream()
                .flatMap(x -> x.getFriends().stream().map(RedisUser::getId))
                .toList());
    }

    private static void setUsedForUserAndHisFriends(Set<String> usedIds, RedisUser user) {
        usedIds.add(user.getId());
        usedIds.addAll(user.getFriends().stream()
                .flatMap(x -> x.getFriends().stream().map(RedisUser::getId))
                .toList());
    }

    private RedisUser updateAddress(RedisUser user) {
        user.setAddress(UUID.randomUUID().toString());
        return user;
    }
}