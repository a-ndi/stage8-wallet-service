package com.stage8.wallet.model.entity;


import com.stage8.wallet.model.enums.TransactionStatus;
import com.stage8.wallet.model.enums.TransactionType;
import jakarta.persistence.*;
import org.apache.catalina.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String reference;

    @ManyToOne
    private User user;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    private BigDecimal amount;

    private LocalDateTime createdAt = LocalDateTime.now();
}
