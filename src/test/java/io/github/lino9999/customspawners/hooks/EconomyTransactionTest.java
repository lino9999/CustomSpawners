package io.github.lino9999.customspawners.hooks;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

class EconomyTransactionTest {
    @Test
    void successfulRefundIsBoundToTheReceiptAndCannotBeAppliedTwice() {
        AtomicInteger oldProviderCredits = new AtomicInteger();
        AtomicInteger newProviderCredits = new AtomicInteger();
        EconomyBridge.Transaction payment =
                new EconomyBridge.Transaction(
                        true,
                        9,
                        "Original PlayerPoints",
                        () -> {
                            oldProviderCredits.addAndGet(9);
                            return true;
                        });
        assertTrue(payment.successful());
        assertEquals(9, payment.amount());
        assertEquals("Original PlayerPoints", payment.provider());
        assertTrue(payment.refund());
        assertTrue(payment.refund());
        assertEquals(9, oldProviderCredits.get());
        assertEquals(0, newProviderCredits.get());
    }

    @Test
    void failedRefundIsNotRetriedAndCannotDuplicateAnUncertainExternalCredit() {
        AtomicInteger calls = new AtomicInteger();
        EconomyBridge.Transaction payment =
                new EconomyBridge.Transaction(
                        true,
                        5,
                        "Provider",
                        () -> {
                            calls.incrementAndGet();
                            return false;
                        });
        assertFalse(payment.refund());
        assertFalse(payment.refund());
        assertEquals(1, calls.get());
    }

    @Test
    void rejectedTransactionsCannotCreditMoneyAndFreeActionsNeedNoProvider() {
        assertFalse(EconomyBridge.Transaction.failed().successful());
        assertFalse(EconomyBridge.Transaction.failed().refund());
        EconomyBridge.Transaction free = EconomyBridge.Transaction.free();
        assertTrue(free.successful());
        assertEquals(0, free.amount());
        assertTrue(free.refund());
        assertTrue(free.refund());
    }
}
