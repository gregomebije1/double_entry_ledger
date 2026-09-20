package com.gregomebije.ledger.model;

import java.util.List;
import com.gregomebije.ledger.model.LedgerLine;
import com.gregomebije.ledger.enumtype.LedgerLineType;
import com.gregomebije.ledger.exception.UnbalancedLedgerException;

public class LedgerValidator {
    /**
     * Validates that the total sum of debits matches the total sum of credits.
     * Processes values safely as minor units (long integers).
     *
     * @param ledgerLines The list of lines to validate
     * @return void if the ledger balances to zero
     */
    public void validate(List<LedgerLine> ledgerLines) {
        long debitAmount = 0; 
        long creditAmount = 0;

        if (ledgerLines == null || ledgerLines.isEmpty())
            throw new IllegalArgumentException("Empty Ledger");

        for (LedgerLine ledgerLine : ledgerLines) {
            if (ledgerLine == null) continue;

            if (ledgerLine.getAmount() <= 0)
                throw new IllegalArgumentException("No negative or zero amount");
            
            if (ledgerLine.getAmount() > Long.MAX_VALUE || ledgerLine.getAmount() < Long.MIN_VALUE)
                throw new IllegalArgumentException("Amount must be within Long.MAX_VALUE and Long.MIN_VALUE");

            if (ledgerLine.getLedgerLineType() == LedgerLineType.DEBIT) {
                debitAmount += ledgerLine.getAmount();
            } else if (ledgerLine.getLedgerLineType() == LedgerLineType.CREDIT) {
                creditAmount += ledgerLine.getAmount();
            }
        }
        long diff = debitAmount - creditAmount;
        if (debitAmount != creditAmount) {
            throw new UnbalancedLedgerException("Unbalanced exception: " + diff);
        }
    }
}