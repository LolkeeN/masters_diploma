package com.ntudp.vasyl.veselov.master;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.ComponentScan;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ImportTestcontainers(InfrastructureDependencies.class)
@SpringBootTest
@EnableConfigurationProperties
@ComponentScan(basePackages = "com.ntudp")
@Execution(ExecutionMode.SAME_THREAD)
public abstract class BaseTestParent {
    
}
