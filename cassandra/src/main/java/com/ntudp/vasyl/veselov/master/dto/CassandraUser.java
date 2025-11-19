package com.ntudp.vasyl.veselov.master.dto;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("user")
public class CassandraUser extends User {

    @PrimaryKeyColumn(
            name = "id",
            ordinal = 0,
            type = PrimaryKeyType.PARTITIONED
    )
    private String id;

    public CassandraUser() {
        this.id = UUID.randomUUID().toString();
    }
}
