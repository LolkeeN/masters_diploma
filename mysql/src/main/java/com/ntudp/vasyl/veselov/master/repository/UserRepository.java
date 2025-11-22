package com.ntudp.vasyl.veselov.master.repository;

import com.ntudp.vasyl.veselov.master.dto.SqlUser;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<SqlUser,String> {

    List<SqlUser> findAll();

    @Query(nativeQuery = true,
            value = "SELECT COUNT(*) FROM (" +
                    "SELECT uf.uzer_id FROM uzer_friends uf " +
                    "GROUP BY uf.uzer_id HAVING COUNT(*) > 1" +
                    ") subquery")
    Long countAllWithMoreThen1Friend();

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM uzer_friends WHERE uzer_id = ?1 OR friends_id = ?1", nativeQuery = true)
    void deleteAllFriendshipsByUserId(String userId);

    @Transactional
    default void deleteUserWithFriendships(String userId) {
        deleteAllFriendshipsByUserId(userId);
        deleteById(userId);
    }

}
