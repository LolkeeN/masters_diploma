package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.MongoUser;
import com.ntudp.vasyl.veselov.master.dto.User;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends MongoRepository<MongoUser, String> {

    List<MongoUser> findAll();
}
