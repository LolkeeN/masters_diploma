package com.ntudp.vasyl.veselov.master.dto;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;

@EqualsAndHashCode(callSuper = true)
@Data
@RedisHash("user")
public class RedisUser extends User{

    @Id
    private String id = UUID.randomUUID().toString();

    @Indexed
    private Set<RedisUser> friends = new HashSet<>();
    public RedisUser() {
        super(new Random());
    }
}
