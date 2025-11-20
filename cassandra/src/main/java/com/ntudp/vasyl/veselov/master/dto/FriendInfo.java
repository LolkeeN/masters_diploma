package com.ntudp.vasyl.veselov.master.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Random;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;
import org.springframework.data.cassandra.core.mapping.UserDefinedType;

@UserDefinedType("friend_info")
@Data
public class FriendInfo {
    private String id;
    private String username;
    private String email;
    private String phone;
    private String address;
    private BigDecimal salary;
    private LocalDate birthDate;

    public FriendInfo() {}

    public FriendInfo(CassandraUser user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.phone = user.getPhone();
        this.address = user.getAddress();
        this.salary = user.getSalary();
        this.birthDate = user.getBirthDate();
    }
}