package com.ntudp.vasyl.veselov.master.dto;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.Frozen;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

@EqualsAndHashCode(callSuper = true)
@Data
@Table("user")
public class CassandraUser extends User {

    @Id
    private String id = UUID.randomUUID().toString();

    // Хранение коллекции объектов (FROZEN)
    @Column("friends")
    @CassandraType(type = CassandraType.Name.SET, typeArguments = {CassandraType.Name.UDT},
            userTypeName = "friend_info")
    @Frozen
    private Set<FriendInfo> friends = new HashSet<>();

    public CassandraUser() {
        super(new Random());
    }
}