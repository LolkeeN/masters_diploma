package com.ntudp.vasyl.veselov.master.dto;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true, exclude = "friends")
@Entity(name = "uzer")
@Data
@Builder
@AllArgsConstructor
public class SqlUser extends User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.DETACH, CascadeType.MERGE, CascadeType.REFRESH})
    @JoinTable(indexes = {
            @Index(name = "idx_uzer_friends_uzer_id", columnList = "uzer_id"),
            @Index(name = "idx_uzer_friends_friends_id", columnList = "friends_id"),
            @Index(name = "idx_uzer_friends_both", columnList = "friends_id, uzer_id"),
            @Index(name = "idx_uzer_friends_both_reversed", columnList = "uzer_id, friends_id")
    })
    private Set<SqlUser> friends = new HashSet<>();

    public SqlUser() {
        super(new Random());
    }
}
