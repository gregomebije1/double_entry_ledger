package com.gregomebije.ledger.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import org.hibernate.annotations.*;

import lombok.*;

import com.gregomebije.ledger.enumtype.LedgerLineType;

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
    
    private String description;

    private LedgerLineType ledgerLineType;

    private long amount;

    public LedgerLine(String description, LedgerLineType ledgerLineType, long amount) {
        this.description = description;
        this.ledgerLineType = ledgerLineType;
        this.amount = amount;
    }
}