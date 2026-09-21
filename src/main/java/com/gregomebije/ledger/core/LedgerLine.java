package com.gregomebije.ledger.core;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.*;

import lombok.*;

import com.gregomebije.ledger.core.LedgerLineType;

@Entity
@Table(name = "ledger_lines")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder

public class LedgerLine {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(nullable = false, unique = true)
    private Long id;
    
    private String accountId;

    private LedgerLineType ledgerLineType;

    private long amount;

    public LedgerLine(String accountId, LedgerLineType ledgerLineType, long amount) {
        this.accountId = accountId;
        this.ledgerLineType = ledgerLineType;
        this.amount = amount;
    }
}