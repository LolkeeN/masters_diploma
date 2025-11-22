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
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.MySQLContainer;
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
    private static final MySQLContainer<?> MYSQL_CONTAINER =
            new MySQLContainer<>("mysql:8.1.0")
                    .withDatabaseName("uzer")
                    .withUsername("test")
                    .withPassword("test")
                    .withSharedMemorySize(8_000_000_000L)
                    .withCommand("mysqld",
                            "--innodb-buffer-pool-size=4G",           // Основной кеш
                            "--innodb-log-file-size=1G",              // Размер логов
                            "--innodb-log-buffer-size=256M",          // Буфер логов
                            "--innodb-flush-log-at-trx-commit=2",     // Производительность
                            "--innodb-flush-method=O_DIRECT",         // Прямой I/O
                            "--innodb-io-capacity=2000",              // SSD IOPS
                            "--innodb-io-capacity-max=4000",
                            "--innodb-read-io-threads=8",             // Потоки чтения
                            "--innodb-write-io-threads=8",            // Потоки записи
                            "--max-connections=1000",                 // Соединения
                            "--thread-cache-size=100",                // Кеш потоков
                            "--query-cache-size=0",                   // Отключить (устарело)
                            "--tmp-table-size=512M",                  // Временные таблицы
                            "--max-heap-table-size=512M",
                            "--sort-buffer-size=8M",                  // Сортировка
                            "--read-buffer-size=2M",                  // Чтение
                            "--join-buffer-size=8M"                   // Джойны
                    )
            ;

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
    @Timeout(value = 3600, unit = TimeUnit.MINUTES)
    void test() throws Exception {
        Random rand = new Random();
        List<SqlUser> users = new ArrayList<>();
        AtomicInteger atomicInteger = new AtomicInteger();
        List<String[]> dataLines = new ArrayList<>();
        Set<String> usedIds = new HashSet<>();
        dataLines.add(new String[] {
                "Operation", "Time (ms)"
        });

        long start = System.currentTimeMillis();
        List<SqlUser> finalUsers = users;
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


                if (finalUsers.size() > 2) {
                    user.getFriends().add(finalUsers.get(rand.nextInt(finalUsers.size() - 1)));
                }

                finalUsers.add(userRepository.save(user));
            }
        });

        synchronized (monitor) {
            monitor.wait();
        }

        dataLines.add(new String[] {
                "Generate starting users", String.valueOf(System.currentTimeMillis() - start)
        });


        log.info("Starting counting users with more than 1 friend");
        SqlUser randomUser = users.get(rand.nextInt(users.size() - 1));

        start = System.currentTimeMillis();
        userRepository.countAllWithMoreThen1Friend();

        dataLines.add(new String[] {
                "Find all and interact with many to many relation", String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("Starting find by id for");
        start = System.currentTimeMillis();
        userRepository.findById(randomUser.getId());
        dataLines.add(new String[] {
                "Find by id", String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        log.info("Starting delete by id with all friendships");
        randomUser = users.get(rand.nextInt(users.size() - 1));
        start = System.currentTimeMillis();
        userRepository.deleteUserWithFriendships(randomUser.getId());
        dataLines.add(new String[] {
                "Delete by id with all friendships", String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        log.info("Starting count all users");
        start = System.currentTimeMillis();
        userRepository.count();
        dataLines.add(new String[] {
                "Count all", String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("Starting update random user");
        start = System.currentTimeMillis();
        randomUser = users.get(rand.nextInt(users.size() - 1));
        randomUser.setEmail(UUID.randomUUID().toString());
        userRepository.save(randomUser);
        dataLines.add(new String[] {
                "Update user", String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        log.info("Starting find 10 percent of users");
        int tenPercent = usersCount / 10;
        Set<SqlUser> tenPercentUsers = users.stream()
                .limit(tenPercent)
                .collect(Collectors.toSet());

        start = System.currentTimeMillis();
        userRepository.findAllById(tenPercentUsers.stream().map(SqlUser::getId).collect(Collectors.toList()));

        dataLines.add(new String[] {
                "Find %s(10 percent) random users".formatted(tenPercent), String.valueOf(System.currentTimeMillis() - start)
        });
        log.info("Starting update 10 percent of users");

        List<SqlUser> updatedTenPercent = tenPercentUsers.stream()
                .map(this::updateAddress)
                .toList();
        start = System.currentTimeMillis();
        userRepository.saveAll(updatedTenPercent);
        dataLines.add(new String[] {
                "Update address for %s(10 percent) random users".formatted(tenPercent), String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        log.info("Starting find 50 percent of users");

        int fiftyPercent = usersCount / 2;
        Set<SqlUser> fiftyPercentUsers = users.stream()
                .limit(fiftyPercent)
                .collect(Collectors.toSet());

        start = System.currentTimeMillis();
        userRepository.findAllById(fiftyPercentUsers.stream().map(SqlUser::getId).collect(Collectors.toList()));

        dataLines.add(new String[] {
                "Find %s(50 percent) random users".formatted(fiftyPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("Starting update 50 percent of users");

        List<SqlUser> updated50Percent = fiftyPercentUsers.stream()
                .map(this::updateAddress)
                .toList();
        start = System.currentTimeMillis();
        userRepository.saveAll(updated50Percent);
        dataLines.add(new String[] {
                "Update address for %s(50 percent) random users".formatted(fiftyPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        log.info("Starting delete 15 percent of users with friends");
        int fifteenPercent = (usersCount * 15) / 100;
        Set<SqlUser> fifteenPercentUsers = users.stream()
                .limit(fifteenPercent)
                .collect(Collectors.toSet());

        start = System.currentTimeMillis();
        fifteenPercentUsers
                .stream()
                .map(SqlUser::getId)
                .forEach(userRepository::deleteUserWithFriendships);
        dataLines.add(new String[]{
                "Delete users with friends for %s(15 percent) random users".formatted(fifteenPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("Starting delete all users");
        start = System.currentTimeMillis();
        userRepository.deleteAll();
        dataLines.add(new String[] {
                "Delete all users", String.valueOf(System.currentTimeMillis() - start)
        });

        CsvUtil.generateFile(usersCount + "_users_mysql_statistics", dataLines);
    }

    private static void setUsedForUserAndHisFriends(Set<String> usedIds, Set<SqlUser> setOfUsers) {
        usedIds.addAll(setOfUsers.stream().map(SqlUser::getId).toList());
        usedIds.addAll(setOfUsers.stream()
                .flatMap(x -> x.getFriends().stream().map(SqlUser::getId))
                .toList());
    }

    private static void setUsedForUserAndHisFriends(Set<String> usedIds, SqlUser user) {
        usedIds.add(user.getId());
        usedIds.addAll(user.getFriends().stream()
                .flatMap(x -> x.getFriends().stream().map(SqlUser::getId))
                .toList());
    }

    private SqlUser updateAddress(SqlUser user) {
        user.setAddress(UUID.randomUUID().toString());
        return user;
    }

}