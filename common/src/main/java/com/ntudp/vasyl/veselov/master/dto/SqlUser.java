package com.ntudp.vasyl.veselov.master.dto;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode(callSuper = true)
@Entity(name = "uzer")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SqlUser extends User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
}
