package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.MongoUser;
import com.ntudp.vasyl.veselov.master.dto.UserFriendship;
import com.ntudp.vasyl.veselov.master.util.ContainerStats;
import com.ntudp.vasyl.veselov.master.util.CsvUtil;
import jakarta.annotation.PostConstruct;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.TestPropertySources;
import org.testcontainers.containers.GenericContainer;
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
    @Autowired
    private UserFriendshipRepository userFriendshipRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Value("${test.users.count}")
    private int usersCount;

    private String stageName;

    @Container
    private static final MongoDBContainer MONGO_CONTAINER =
            new MongoDBContainer("mongo:latest")
                    .withSharedMemorySize(8_000_000_000L);


    @PostConstruct
    public void createIndexes() throws Exception {
        // Индекс на ID (если не String _id)
        mongoTemplate.indexOps(MongoUser.class)
                .ensureIndex(new Index("id", Sort.Direction.ASC));

        // Индексы для поиска друзей
        mongoTemplate.indexOps("user_friends")  // коллекция связей
                .ensureIndex(new Index("userId", Sort.Direction.ASC));

        mongoTemplate.indexOps("user_friends")
                .ensureIndex(new Index("friendId", Sort.Direction.ASC));

        // Составной индекс для связей
        mongoTemplate.indexOps("user_friends")
                .ensureIndex(new Index()
                        .on("userId", Sort.Direction.ASC)
                        .on("friendId", Sort.Direction.ASC));
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO_CONTAINER::getConnectionString);
        registry.add("spring.data.mongodb.database", () -> "test");
    }

    @Test
    @Timeout(value = 3600, unit = TimeUnit.MINUTES)
    void test() throws Exception {
        Random rand = new Random();
        List<MongoUser> users = new ArrayList<>();
        AtomicInteger atomicInteger = new AtomicInteger();
        List<String[]> dataLines = new ArrayList<>();
        List<ContainerStats> metrics = new ArrayList<>();

        dataLines.add(new String[] {
                "Operation", "Time (ms)"
        });

        stageName = "user generation";
        long start = System.currentTimeMillis();
        List<MongoUser> finalUsers = users;
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
                MongoUser user = new MongoUser();


                if (finalUsers.size() > 2) {
                    UserFriendship friendship = new UserFriendship(user.getId(), finalUsers.get(rand.nextInt(finalUsers.size() - 1)).getId());
                    user.getFriends().add(userFriendshipRepository.save(friendship));
                }

                finalUsers.add(userRepository.save(user));
            }
        });
        Executors.newFixedThreadPool(1).execute(() -> {
            while (MONGO_CONTAINER.isRunning()) {
                metrics.add(collectStats(MONGO_CONTAINER));
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

        MongoUser randomUser = users.get(rand.nextInt(users.size() - 1));
        dataLines.add(new String[] {
                "Generate starting users", String.valueOf(System.currentTimeMillis() - start)
        });

        stageName = "count all with more than 1 friend";
        log.info("Find all and interact with many to many relation");
        start = System.currentTimeMillis();
        userRepository.findAll()
                .stream()
                .filter(u -> u.getFriends().size() > 1)
                .count();

        dataLines.add(new String[] {
                "Find all and interact with many to many relation", String.valueOf(System.currentTimeMillis() - start)
        });

        stageName = "find 1 by id";
        log.info("Find by id");
        start = System.currentTimeMillis();
        userRepository.findById(randomUser.getId());
        dataLines.add(new String[] {
                "Find by id", String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();
        randomUser = users.get(rand.nextInt(users.size() - 1));

        stageName = "delete 1 by id with friendships";
        log.info("Delete by id");

        start = System.currentTimeMillis();
        userRepository.deleteById(randomUser.getId());
        dataLines.add(new String[] {
                "Delete by id with all friendships", String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        stageName = "count all";
        log.info("Count all");
        start = System.currentTimeMillis();
        userRepository.count();
        dataLines.add(new String[] {
                "Count all", String.valueOf(System.currentTimeMillis() - start)
        });

        stageName = "update 1 random";
        log.info("Update user");
        start = System.currentTimeMillis();
        randomUser = users.get(rand.nextInt(users.size() - 1));
        randomUser.setEmail(UUID.randomUUID().toString());
        userRepository.save(randomUser);
        dataLines.add(new String[] {
                "Update user", String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        stageName = "find 10 percent";
        log.info("find 10 percent users");
        int tenPercent = usersCount / 10;
        Set<MongoUser> tenPercentUsers = users.stream()
                .limit(tenPercent)
                .collect(Collectors.toSet());

        start = System.currentTimeMillis();
        userRepository.findAllById(tenPercentUsers.stream().map(MongoUser::getId).collect(Collectors.toList()));

        dataLines.add(new String[] {
                "Find %s(10 percent) random users".formatted(tenPercent), String.valueOf(System.currentTimeMillis() - start)
        });

        stageName = "update 10 percent";
        log.info("update 10 percent users");

        List<MongoUser> updatedTenPercent = tenPercentUsers.stream()
                .map(this::updateAddress)
                .toList();
        start = System.currentTimeMillis();
        userRepository.saveAll(updatedTenPercent);
        dataLines.add(new String[] {
                "Update address for %s(10 percent) random users".formatted(tenPercent), String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        stageName = "find 50 percent";
        log.info("find 50 percent users");

        int fiftyPercent = usersCount / 2;
        Set<MongoUser> fiftyPercentUsers = users.stream()
                .limit(fiftyPercent)
                .collect(Collectors.toSet());

        start = System.currentTimeMillis();
        userRepository.findAllById(fiftyPercentUsers.stream().map(MongoUser::getId).collect(Collectors.toList()));

        dataLines.add(new String[] {
                "Find %s(10 percent) random users".formatted(fiftyPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });

        stageName = "update 50 percent";
        log.info("update 50 percent users");

        List<MongoUser> updated50Percent = fiftyPercentUsers.stream()
                .map(this::updateAddress)
                .toList();
        start = System.currentTimeMillis();
        userRepository.saveAll(updated50Percent);
        dataLines.add(new String[] {
                "Update address for %s(50 percent) random users".formatted(fiftyPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });
        users = userRepository.findAll();

        stageName = "find 15 percent";
        log.info("find 15 percent users");

        int fifteenPercent = (usersCount * 15) / 100;
        Set<MongoUser> fifteenPercentUsers = users.stream()
                .limit(fifteenPercent)
                .collect(Collectors.toSet());

        stageName = "delete 15 percent with friends";
        log.info("delete 15 percent users with friends");

        start = System.currentTimeMillis();
        fifteenPercentUsers
                .stream()
                .forEach(user -> {
                    // Удаляем связи
                    userFriendshipRepository.deleteById(user.getId());
                    userFriendshipRepository.deleteAllByFriendId(user.getId());
                    // Удаляем пользователя
                    userRepository.deleteById(user.getId());
                });
        dataLines.add(new String[]{
                "Delete users with friends for %s(15 percent) random users".formatted(fifteenPercentUsers.size()), String.valueOf(System.currentTimeMillis() - start)
        });

        stageName = "delete all";
        log.info("delete all users");
        start = System.currentTimeMillis();
        userFriendshipRepository.deleteAll();
        userRepository.deleteAll();
        dataLines.add(new String[] {
                "Delete all users", String.valueOf(System.currentTimeMillis() - start)
        });
        MONGO_CONTAINER.stop();

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

        CsvUtil.generateFile(usersCount + "_users_mongo_TIME_statistics", dataLines);
        CsvUtil.generateFile(usersCount + "_users_mongo_CONTAINER_statistics", metricDataLines);
    }


    private MongoUser updateAddress(MongoUser user) {
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