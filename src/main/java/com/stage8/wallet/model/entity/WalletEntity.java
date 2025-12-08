package com.stage8.wallet.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.apache.catalina.User;

import java.math.BigDecimal;

@Entity
@Builder
@AllArgsConstructor
public class WalletEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    private User user;

    private String walletNumber;

    private BigDecimal balance = BigDecimal.ZERO;
}
