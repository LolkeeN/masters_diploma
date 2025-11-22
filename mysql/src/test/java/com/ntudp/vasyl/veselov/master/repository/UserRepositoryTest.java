package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.SqlUser;
import com.ntudp.vasyl.veselov.master.dto.User;
import com.ntudp.vasyl.veselov.master.util.ContainerStats;
import com.ntudp.vasyl.veselov.master.util.CsvUtil;
import java.io.BufferedReader;
import java.io.InputStreamReader;
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
import org.testcontainers.containers.GenericContainer;
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

    private String stageName;

    @Value("${test.users.count}")
    private int usersCount;

    @Container
    private static final MySQLContainer<?> MYSQL_CONTAINER =
            new MySQLContainer<>("mysql:8.1.0")
                    .withDatabaseName("uzer")
                    .withUsername("test")
                    .withPassword("test")
                    .withSharedMemorySize(8_000_000_000L)
                    .withCommand("mysqld",
                            "--innodb-buffer-pool-size=2G",
                            "--innodb-flush-log-at-trx-commit=1",
                            "--sync-binlog=1",
                            "--innodb-doublewrite=1",
                            "--innodb-log-file-size=512M",
                            "--max-connections=1000",
                            "--skip-name-resolve",
                            "--max-allowed-packet=64M"
                    );

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
        List<ContainerStats> metrics = new ArrayList<>();
        dataLines.add(new String[] {
                "Operation", "Time (ms)"
        });

        stageName = "user generation";
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
        Executors.newFixedThreadPool(1).execute(() -> {
            while (MYSQL_CONTAINER.isRunning()) {
                metrics.add(collectStats(MYSQL_CONTAINER));
                try {
                    Thread.sleep(usersCount / 10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        synchronized (monitor) {
            monitor.wait();
        }

        dataLines.add(new String[] {
                "Generate starting users", String.valueOf(System.currentTimeMillis() - start)
        });


        stageName = "count all with more than 1 friend";
        log.info("Starting counting users with more than 1 friend");
        SqlUser randomUser = users.get(rand.nextInt(users.size() - 1));

        start = System.currentTimeMillis();
        userRepository.countAllWithMoreThen1Friend();

        dataLines.add(new String[] {
                "Find all and interact with many to many relation", String.valueOf(System.currentTimeMillis() - start)
        });

        stageName = "find 1 by id";
        log.info("Starting find by id for");
        start = System.currentTimeMillis();
        userRepository.findById(randomUser.getId());
        dataLines.add(new String[] {
                "Find by id", String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        stageName = "delete 1 by id with friendships";
        log.info("Starting delete by id with all friendships");
        randomUser = users.get(rand.nextInt(users.size() - 1));
        start = System.currentTimeMillis();
        userRepository.deleteUserWithFriendships(randomUser.getId());
        dataLines.add(new String[] {
                "Delete by id with all friendships", String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        stageName = "count all";
        log.info("Starting count all users");
        start = System.currentTimeMillis();
        userRepository.count();
        dataLines.add(new String[] {
                "Count all", String.valueOf(System.currentTimeMillis() - start)
        });

        stageName = "update 1 random";
        log.info("Starting update random user");
        start = System.currentTimeMillis();
        randomUser = users.get(rand.nextInt(users.size() - 1));
        randomUser.setEmail(UUID.randomUUID().toString());
        userRepository.save(randomUser);
        dataLines.add(new String[] {
                "Update user", String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        stageName = "find 10 percent";
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

        stageName = "update 10 percent";
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

        stageName = "find 50 percent";
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

        stageName = "update 50 percent";
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

        stageName = "find 15 percent";
        log.info("Starting delete 15 percent of users with friends");
        int fifteenPercent = (usersCount * 15) / 100;
        Set<SqlUser> fifteenPercentUsers = users.stream()
                .limit(fifteenPercent)
                .collect(Collectors.toSet());

        stageName = "delete 15 percent with friends";
        start = System.currentTimeMillis();
        fifteenPercentUsers
                .stream()
                .map(SqlUser::getId)
                .forEach(userRepository::deleteUserWithFriendships);
        dataLines.add(new String[]{
                "Delete users with friends for %s(15 percent) random users".formatted(fifteenPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });

        stageName = "delete all";
        log.info("Starting delete all users");
        start = System.currentTimeMillis();
        userRepository.deleteAll();
        dataLines.add(new String[] {
                "Delete all users", String.valueOf(System.currentTimeMillis() - start)
        });

        MYSQL_CONTAINER.stop();

        List<String[]> metricDataLines = new ArrayList<>();
        metricDataLines.add(new String[]{
                "Stage name",
                "Timestamp",
                "CPU percentage",
                "Memory Usage bytes",
                "Memory limit bytes",
                "Disk read bytes",
                "Disk write bytes",
                "Network received bytes",
                "Network sent bytes"
        });
        for (ContainerStats metric : metrics) {
            metricDataLines.add(new String[]{
                    metric.getStageName(),
                    String.valueOf(metric.getTimestamp()),
                    String.valueOf(metric.getCpuPercentage()),
                    String.valueOf(metric.getMemoryUsageBytes()),
                    String.valueOf(metric.getMemoryLimitBytes()),
                    String.valueOf(metric.getDiskReadBytes()),
                    String.valueOf(metric.getDiskWriteBytes()),
                    String.valueOf(metric.getNetworkReceivedBytes()),
                    String.valueOf(metric.getNetworkSentBytes())
            });
        }

        CsvUtil.generateFile(usersCount + "_users_mySQL_TIME_statistics", dataLines);
        CsvUtil.generateFile(usersCount + "_users_mySQL_CONTAINER_statistics", metricDataLines);
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

    private ContainerStats collectStats(GenericContainer<?> container) {
        try {
            // Используем команду docker stats напрямую
            String containerId = container.getContainerId();

            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "stats", "--no-stream", "--format",
                    "{{.CPUPerc}},{{.MemUsage}},{{.NetIO}},{{.BlockIO}}",
                    containerId
            );

            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {

                String line = reader.readLine();
                if (line != null) {
                    return parseDockerStatsOutput(line);
                }
            }

            process.waitFor(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            log.warn("Failed to collect stats via docker command: {}", e.getMessage());
        }

        return new ContainerStats(stageName, 0, 0, 0, 0, 0, 0, 0, System.currentTimeMillis());
    }

    private ContainerStats parseDockerStatsOutput(String line) {
        // Парсим: "100.85%,1.15GiB / 15.44GiB,256MB / 129MB,0B / 0B"
        String[] parts = line.split(",");

        double cpuPercent = parseCpuPercent(parts[0]);           // "100.85%" -> 100.85
        long[] memory = parseMemoryUsage(parts[1]);             // "1.15GiB / 15.44GiB" -> [bytes, limit]
        long[] network = parseNetworkIO(parts[2]);              // "256MB / 129MB" -> [rx, tx]
        long[] disk = parseDiskIO(parts[3]);                    // "0B / 0B" -> [read, write]

        return new ContainerStats(
                stageName,
                cpuPercent,
                memory[0],      // usage
                memory[1],      // limit
                disk[0],        // read
                disk[1],        // write
                network[0],     // received
                network[1],     // sent
                System.currentTimeMillis()
        );
    }

    private double parseCpuPercent(String cpuStr) {
        return Double.parseDouble(cpuStr.replace("%", ""));
    }

    private long[] parseMemoryUsage(String memStr) {
        // "1.15GiB / 15.44GiB" -> [1235000000L, 16583000000L]
        String[] parts = memStr.split(" / ");
        return new long[]{
                parseBytes(parts[0].trim()),
                parseBytes(parts[1].trim())
        };
    }

    private long parseBytes(String sizeStr) {
        // "1.15GiB" -> 1235000000L
        String numStr = sizeStr.replaceAll("[^0-9.]", "");
        double value = Double.parseDouble(numStr);

        if (sizeStr.contains("GiB") || sizeStr.contains("GB")) {
            return (long) (value * 1_073_741_824); // 1024^3
        } else if (sizeStr.contains("MiB") || sizeStr.contains("MB")) {
            return (long) (value * 1_048_576); // 1024^2
        } else if (sizeStr.contains("KiB") || sizeStr.contains("KB")) {
            return (long) (value * 1024);
        }

        return (long) value;
    }

    private long[] parseNetworkIO(String networkStr) {
        // "256MB / 129MB" -> [268435456L, 135266304L] (received / sent)
        try {
            String[] parts = networkStr.split(" / ");
            return new long[]{
                    parseBytes(parts[0].trim()),  // received
                    parseBytes(parts[1].trim())   // sent
            };
        } catch (Exception e) {
            return new long[]{0L, 0L};
        }
    }

    private long[] parseDiskIO(String diskStr) {
        // "0B / 0B" -> [0L, 0L] (read / write)
        try {
            String[] parts = diskStr.split(" / ");
            return new long[]{
                    parseBytes(parts[0].trim()),  // read
                    parseBytes(parts[1].trim())   // write
            };
        } catch (Exception e) {
            return new long[]{0L, 0L};
        }
    }
}