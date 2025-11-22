package com.ntudp.vasyl.veselov.master.dto;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "user_friends")
public class UserFriendship {

    @Id
    private String id;

    @Indexed  // ← Индекс на пользователя
    private String userId;

    @Indexed  // ← Индекс на друга
    private String friendId;

    public UserFriendship(String userId, String friendId) {
        this.userId = userId;
        this.friendId = friendId;
    }
}
