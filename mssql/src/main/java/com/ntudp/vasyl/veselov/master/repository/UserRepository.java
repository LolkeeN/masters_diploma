package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.SqlUser;
import com.ntudp.vasyl.veselov.master.dto.User;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<SqlUser, String> {

    List<SqlUser> findAll();

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM uzer_friends WHERE uzer_id = ?1 OR friends_id = ?1", nativeQuery = true)
    void deleteAllFriendshipsByUserId(String userId);

    @Transactional
    default void deleteUserWithFriendships(String userId) {
        deleteAllFriendshipsByUserId(userId);
        deleteById(userId);
    }

    @Query(value = """
    SELECT u.* FROM uzer u 
    WHERE u.id IN (
        SELECT CAST(TRIM(value) AS uniqueidentifier) 
        FROM STRING_SPLIT(:idsString, ',')
        WHERE TRIM(value) != ''
    )
    """, nativeQuery = true)
    List<SqlUser> findAllByIdsString(@Param("idsString") String idsString);
}
