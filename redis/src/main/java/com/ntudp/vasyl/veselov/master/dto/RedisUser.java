package com.ntudp.vasyl.veselov.master.dto;

import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;

@RedisHash("user")
public class RedisUser extends User{

    @Id
    private String id;

    public RedisUser() {
        this.id = UUID.randomUUID().toString();
    }
}
