package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.SqlUser;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends CrudRepository<SqlUser,String> {

}
