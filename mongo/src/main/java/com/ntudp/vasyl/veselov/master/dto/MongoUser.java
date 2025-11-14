package com.ntudp.vasyl.veselov.master.dto;

import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

@EqualsAndHashCode(callSuper = true)
@Data
@Document
public class MongoUser extends User {

    @MongoId
    private final String id = UUID.randomUUID().toString();
}
