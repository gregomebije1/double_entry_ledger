package com.gregomebije.ledger.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LedgerServiceTest {

    private final LedgerService service =
            new LedgerService(new LedgerValidator());

    @Test
    void unbalancedTransactionFailsBeforePersistenceLayerWouldBeReached() {
        assertThrows(UnbalancedLedgerException.class, () ->
                service.postTransaction(
                        "tenant-a",
                        new PostTransactionRequest(
                                "invalid",
                                List.of(
                                        new LedgerLine("a", LedgerLineType.DEBIT, 2500L),
                                        new LedgerLine("b", LedgerLineType.CREDIT, 2499L)
                                )
                        )
                ));
    }

    @Test
    void balancedTransactionIsAccepted() {
        assertDoesNotThrow(() ->
                service.postTransaction(
                        "tenant-a",
                        new PostTransactionRequest(
                                "valid",
                                List.of(
                                        new LedgerLine("a", LedgerLineType.DEBIT, 2500L),
                                        new LedgerLine("b", LedgerLineType.CREDIT, 2500L)
                                )
                        )
                ));
    }

    @Test
    void forcedFailureAfterValidationIsPropagatedForRollback() {
        assertThrows(RuntimeException.class, () ->
                service.postTransactionAndFail(
                        "tenant-a",
                        new PostTransactionRequest(
                                "rollback",
                                List.of(
                                        new LedgerLine("a", LedgerLineType.DEBIT, 1000L),
                                        new LedgerLine("b", LedgerLineType.CREDIT, 1000L)
                                )
                        )
                ));
    }
}
