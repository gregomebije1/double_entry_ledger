package com.gregomebije.ledger;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import com.gregomebije.ledger.core.*;

class LedgerValidatorTest {

    private final LedgerValidator validator = new LedgerValidator();

    @Test
    void balancedDebitAndCreditShouldBeAccepted() {
        var lines = List.of(
                new LedgerLine("account-a", LedgerLineType.DEBIT, 2500L),
                new LedgerLine("account-b", LedgerLineType.CREDIT, 2500L)
        );

        assertDoesNotThrow(() -> validator.validate(lines));
    }

    @Test
    void multipleDebitsAndCreditsShouldBalance() {
        var lines = List.of(
                new LedgerLine("account-a", LedgerLineType.DEBIT, 1000L),
                new LedgerLine("account-b", LedgerLineType.DEBIT, 1500L),
                new LedgerLine("account-c", LedgerLineType.CREDIT, 2500L)
        );

        assertDoesNotThrow(() -> validator.validate(lines));
    }

    @Test
    void unbalancedLedgerShouldThrowExplicitException() {
        var lines = List.of(
                new LedgerLine("account-a", LedgerLineType.DEBIT, 2500L),
                new LedgerLine("account-b", LedgerLineType.CREDIT, 2499L)
        );

        var exception = assertThrows(
                UnbalancedLedgerException.class,
                () -> validator.validate(lines)
        );

        assertTrue(exception.getMessage().contains("1"));
    }

    @Test
    void debitOnlyLedgerShouldBeRejected() {
        var lines = List.of(
                new LedgerLine("account-a", LedgerLineType.DEBIT, 1000L)
        );

        assertThrows(
                UnbalancedLedgerException.class,
                () -> validator.validate(lines)
        );
    }

    @Test
    void creditOnlyLedgerShouldBeRejected() {
        var lines = List.of(
                new LedgerLine("account-a", LedgerLineType.CREDIT, 1000L)
        );

        assertThrows(
                UnbalancedLedgerException.class,
                () -> validator.validate(lines)
        );
    }

    @Test
    void emptyLedgerShouldBeRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(List.of())
        );
    }

    @Test
    void negativeAmountShouldBeRejected() {
        var lines = List.of(
                new LedgerLine("account-a", LedgerLineType.DEBIT, -100L),
                new LedgerLine("account-b", LedgerLineType.CREDIT, -100L)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(lines)
        );
    }

    @Test
    void zeroAmountShouldBeRejected() {
        var lines = List.of(
                new LedgerLine("account-a", LedgerLineType.DEBIT, 0L),
                new LedgerLine("account-b", LedgerLineType.CREDIT, 0L)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(lines)
        );
    }

    @Test
    void LongMaxValueShouldBeHandledWithoutFloatingPointMath() {
        var lines = List.of(
                new LedgerLine("a", LedgerLineType.DEBIT, Long.MAX_VALUE),
                new LedgerLine("b", LedgerLineType.CREDIT, Long.MAX_VALUE)
        );

        assertDoesNotThrow(() -> validator.validate(lines));
    }

    @Test
    void balancingShouldUseMinorUnitsAsIntegers() {
        var lines = List.of(
                new LedgerLine("a", LedgerLineType.DEBIT, 1L),
                new LedgerLine("b", LedgerLineType.CREDIT, 1L)
        );

        assertDoesNotThrow(() -> validator.validate(lines));
    }

    @Test
    void longOverflowShouldBeRejected() {
        assertThrows(ArithmeticException.class, () ->
                validator.validate(List.of(
                        new LedgerLine("a", LedgerLineType.DEBIT, Long.MAX_VALUE),
                        new LedgerLine("b", LedgerLineType.DEBIT, 1L),
                        new LedgerLine("c", LedgerLineType.CREDIT, Long.MAX_VALUE)
                )));
    }
}