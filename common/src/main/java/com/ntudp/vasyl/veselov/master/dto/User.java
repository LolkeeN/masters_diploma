package com.ntudp.vasyl.veselov.master.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import lombok.Data;

@Data
public class User {

    private String username;
    private String password;
    private String email;
    private LocalDate birthDate;
    private String phone;
    private BigDecimal salary;
    private String address;

    public User() {
    }

    public User(Random rand) {
        setAddress(UUID.randomUUID().toString());
        setEmail(UUID.randomUUID().toString());
        setPhone(UUID.randomUUID().toString());
        setSalary(BigDecimal.valueOf(rand.nextDouble()));
        setUsername(UUID.randomUUID().toString());
        setPassword(UUID.randomUUID().toString());
        setBirthDate(LocalDate.now());
    }
}
