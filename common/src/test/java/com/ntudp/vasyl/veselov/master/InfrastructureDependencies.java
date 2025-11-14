package com.ntudp.vasyl.veselov.master;

import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@ImportTestcontainers(InfrastructureDependencies.class)
public interface InfrastructureDependencies {

}