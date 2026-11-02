package com.gregomebije.ledger.utility;

import java.util.List;

import org.springframework.stereotype.Component;
import com.gregomebije.ledger.dto.Models.LedgerLine;
import com.gregomebije.ledger.dto.Models.LedgerLineType;
import com.gregomebije.ledger.exception.UnbalancedLedgerException;

@Component
public class LedgerValidator {
    /**
     * Validates that the total sum of debits matches the total sum of credits.
     * Processes values safely as minor units (long integers).
     *
     * @param ledgerLines The list of lines to validate
     * @return void if the ledger balances to zero
     */
    public void validate(List<LedgerLine> ledgerLines) {
        long debits = 0; 
        long credits = 0;

        if (ledgerLines == null || ledgerLines.isEmpty())
            throw new IllegalArgumentException("Ledger must contain at least one line");

        for (LedgerLine ledgerLine : ledgerLines) {
            if (ledgerLine == null || ledgerLine.accountId() == null) {
                throw new IllegalArgumentException("Account ID is required");
            }
            if ((ledgerLine.debit() < 0) || (ledgerLine.credit() < 0))
                throw new IllegalArgumentException("Amount must be positive");
            
            if ((ledgerLine.debit() > Long.MAX_VALUE || ledgerLine.debit() < Long.MIN_VALUE) ||
               (ledgerLine.credit() > Long.MAX_VALUE || ledgerLine.credit() < Long.MIN_VALUE))
                throw new IllegalArgumentException("Amount must be within Long.MAX_VALUE and Long.MIN_VALUE");

            debits = Math.addExact(debits, ledgerLine.debit()); //Prevents silent long overflows
            credits = Math.addExact(credits, ledgerLine.credit()); 
            
        }
        if (debits != credits) {
            throw new UnbalancedLedgerException("Ledger is unbalanced: variance=" + (debits - credits));
        }
    }
}