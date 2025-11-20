package com.ntudp.vasyl.veselov.master.dto;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.MongoId;

@EqualsAndHashCode(callSuper = true)
@Data
@Document
public class MongoUser extends User {

    @MongoId
    private String id = UUID.randomUUID().toString();

    @Field("friends")
    private Set<MongoUser> friends = new HashSet<>();

    public MongoUser() {
        super(new Random());
    }
}
