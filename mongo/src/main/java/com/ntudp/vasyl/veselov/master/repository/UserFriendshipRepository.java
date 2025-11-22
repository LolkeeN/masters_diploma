package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.UserFriendship;
import java.util.UUID;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserFriendshipRepository extends MongoRepository<UserFriendship, String> {

    void deleteAllByFriendId(String friendId);
}
