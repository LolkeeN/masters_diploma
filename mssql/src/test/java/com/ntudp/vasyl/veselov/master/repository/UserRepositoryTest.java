package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.SqlUser;
import com.ntudp.vasyl.veselov.master.util.ContainerStats;
import com.ntudp.vasyl.veselov.master.util.CsvUtil;
import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
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
import javax.sql.DataSource;
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
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

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
    @Autowired
    private DataSource dataSource;

    @Value("${test.users.count}")
    private int usersCount;

    @Container
    private static final MSSQLServerContainer<?> MS_SQL_CONTAINER =
            new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
                    .acceptLicense()
                    .withPassword("yourStrong(!)Password")
                    .withEnv("MSSQL_MEMORY_LIMIT_MB", "6144")
                    .withSharedMemorySize(8_000_000_000L)
                    .waitingFor(Wait.forLogMessage(".*SQL Server is now ready.*", 1));

    // + добавить @PostConstruct для T-SQL настроек:
    @PostConstruct
    public void configureMSSQL() throws SQLException {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {

            stmt.execute("EXEC sp_configure 'show advanced options', 1");
            stmt.execute("RECONFIGURE");

            stmt.execute("EXEC sp_configure 'max server memory (MB)', 6144");
            stmt.execute("RECONFIGURE");

            stmt.execute("ALTER DATABASE CURRENT SET RECOVERY SIMPLE");
            stmt.execute("ALTER DATABASE CURRENT SET AUTO_CREATE_STATISTICS ON");
            stmt.execute("ALTER DATABASE CURRENT SET AUTO_UPDATE_STATISTICS ON");

            log.info("MSSQL configured successfully");

        } catch (SQLException e) {
            log.warn("MSSQL optimization failed: {}", e.getMessage());
            // Если не работает - просто продолжаем без оптимизаций
        }
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MS_SQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MS_SQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MS_SQL_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.microsoft.sqlserver.jdbc.SQLServerDriver");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.SQLServer2012Dialect");
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
            while (MS_SQL_CONTAINER.isRunning()) {
                metrics.add(collectStats(MS_SQL_CONTAINER));
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

        SqlUser randomUser = users.get(rand.nextInt(users.size() - 1));
        dataLines.add(new String[] {
                "Generate starting users", String.valueOf(System.currentTimeMillis() - start)
        });


        log.info("Starting counting all users with more than 1 friend");
        start = System.currentTimeMillis();
        userRepository.countAllWithMoreThen1Friend();

        dataLines.add(new String[] {
                "Find all and interact with many to many relation", String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("Find 1 random user by id");

        start = System.currentTimeMillis();
        userRepository.findById(randomUser.getId());
        dataLines.add(new String[] {
                "Find by id", String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("Delete 1 random user with all friendships");
        start = System.currentTimeMillis();
        userRepository.deleteUserWithFriendships(randomUser.getId());
        dataLines.add(new String[] {
                "Delete by id with all friendships", String.valueOf(System.currentTimeMillis() - start)
        });

        users = userRepository.findAll();

        log.info("count all users");
        start = System.currentTimeMillis();
        userRepository.count();
        dataLines.add(new String[] {
                "Count all", String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("Update 1 random user");
        start = System.currentTimeMillis();
        randomUser = users.get(rand.nextInt(users.size() - 1));
        randomUser.setEmail(UUID.randomUUID().toString());
        userRepository.save(randomUser);
        dataLines.add(new String[] {
                "Update user", String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        log.info("Find 10 percent random users");
        int tenPercent = usersCount / 10;
        Set<SqlUser> tenPercentUsers = users.stream()
                .limit(tenPercent)
                .collect(Collectors.toSet());

        start = System.currentTimeMillis();
        findAllByIdsFast(tenPercentUsers.stream().map(SqlUser::getId).collect(Collectors.toList()));

        dataLines.add(new String[] {
                "Find %s(10 percent) random users".formatted(tenPercent), String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("update 10 percent random users");
        List<SqlUser> updatedTenPercent = tenPercentUsers.stream()
                .map(this::updateAddress)
                .toList();
        start = System.currentTimeMillis();
        userRepository.saveAll(updatedTenPercent);
        dataLines.add(new String[] {
                "Update address for %s(10 percent) random users".formatted(tenPercent), String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        log.info("Find 50 percent random users");
        int fiftyPercent = usersCount / 2;
        Set<SqlUser> fiftyPercentUsers = users.stream()
                .limit(fiftyPercent)
                .collect(Collectors.toSet());

        start = System.currentTimeMillis();
        findAllByIdsFast(fiftyPercentUsers.stream().map(SqlUser::getId).collect(Collectors.toList()));

        dataLines.add(new String[] {
                "Find %s(50 percent) random users".formatted(fiftyPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("Update 50 percent random users");
        List<SqlUser> updated50Percent = fiftyPercentUsers.stream()
                .map(this::updateAddress)
                .toList();
        start = System.currentTimeMillis();
        userRepository.saveAll(updated50Percent);
        dataLines.add(new String[] {
                "Update address for %s(50 percent) random users".formatted(fiftyPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        log.info("Find 15 percent random users");

        int fifteenPercent = (usersCount * 15) / 100;
        Set<SqlUser> fifteenPercentUsers = users.stream()
                .limit(fifteenPercent)
                .collect(Collectors.toSet());

        log.info("Delete 15 percent random users with friendships");
        start = System.currentTimeMillis();
        fifteenPercentUsers
                .stream()
                .map(SqlUser::getId)
                .forEach(userRepository::deleteUserWithFriendships);
        dataLines.add(new String[]{
                "Delete users with friends for %s(15 percent) random users".formatted(fifteenPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });

        log.info("Delete all users");
        start = System.currentTimeMillis();
        userRepository.deleteAll();
        dataLines.add(new String[] {
                "Delete all users", String.valueOf(System.currentTimeMillis() - start)
        });

        MS_SQL_CONTAINER.stop();

        List<String[]> metricDataLines = new ArrayList<>();
        metricDataLines.add(new String[]{
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

        CsvUtil.generateFile(usersCount + "_users_msSQL_TIME_statistics", dataLines);
        CsvUtil.generateFile(usersCount + "_users_msSQL_CONTAINER_statistics", metricDataLines);
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
    private List<SqlUser> findAllByIdsFast(List<String> ids) {
        String idsString = String.join(",", ids);
        return userRepository.findAllByIdsString(idsString);
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

        return new ContainerStats(0, 0, 0, 0, 0, 0, 0, System.currentTimeMillis());
    }

    private ContainerStats parseDockerStatsOutput(String line) {
        // Парсим: "100.85%,1.15GiB / 15.44GiB,256MB / 129MB,0B / 0B"
        String[] parts = line.split(",");

        double cpuPercent = parseCpuPercent(parts[0]);           // "100.85%" -> 100.85
        long[] memory = parseMemoryUsage(parts[1]);             // "1.15GiB / 15.44GiB" -> [bytes, limit]
        long[] network = parseNetworkIO(parts[2]);              // "256MB / 129MB" -> [rx, tx]
        long[] disk = parseDiskIO(parts[3]);                    // "0B / 0B" -> [read, write]

        return new ContainerStats(
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