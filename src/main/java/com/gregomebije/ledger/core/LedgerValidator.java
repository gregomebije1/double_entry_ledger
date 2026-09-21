package com.gregomebije.ledger.core;

import java.util.List;
import com.gregomebije.ledger.core.LedgerLine;
import com.gregomebije.ledger.core.LedgerLineType;
import com.gregomebije.ledger.core.UnbalancedLedgerException;

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
            if (ledgerLine == null || ledgerLine.getAccountId() == null || ledgerLine.getAccountId().isBlank()) {
                throw new IllegalArgumentException("Account ID is required");
            }
            if (ledgerLine.getAmount() <= 0)
                throw new IllegalArgumentException("Amount must be positive");
            
            if (ledgerLine.getAmount() > Long.MAX_VALUE || ledgerLine.getAmount() < Long.MIN_VALUE)
                throw new IllegalArgumentException("Amount must be within Long.MAX_VALUE and Long.MIN_VALUE");

            if (ledgerLine.getLedgerLineType() == LedgerLineType.DEBIT) {
                debits = Math.addExact(debits, ledgerLine.getAmount()); //Prevents silent long overflows
            } else if (ledgerLine.getLedgerLineType() == LedgerLineType.CREDIT) {
                credits = Math.addExact(credits, ledgerLine.getAmount()); 
            } else {
                throw new IllegalArgumentException("Unsupported ledger type");
            }
        }
        if (debits != credits) {
            throw new UnbalancedLedgerException("Ledger is unbalanced: variance=" + (debits - credits));
        }
    }
}