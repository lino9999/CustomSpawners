package io.github.lino9999.customspawners.storage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

class StateNumberTest {
    @Test
    void acceptsOnlyExactNumbersWithinTheRequestedRange() throws Exception {
        assertEquals(-17, StateStore.exact(-17, Integer.MIN_VALUE, Integer.MAX_VALUE, "x"));
        assertEquals(17, StateStore.exact(new BigDecimal("17.0"), 0, 100, "amount"));
        assertEquals(Long.MAX_VALUE, StateStore.exact(Long.MAX_VALUE, 0, Long.MAX_VALUE, "count"));
        assertEquals(Long.MIN_VALUE, StateStore.exact(Long.MIN_VALUE, Long.MIN_VALUE, 0, "x"));
    }

    @Test
    void rejectsTruncationOverflowAndCoercedNonNumbers() {
        assertThrows(IOException.class, () -> StateStore.exact(1.5, 0, 100, "amount"));
        assertThrows(
                IOException.class,
                () -> StateStore.exact(4_294_967_297L, Integer.MIN_VALUE, Integer.MAX_VALUE, "x"));
        assertThrows(
                IOException.class,
                () ->
                        StateStore.exact(
                                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                                0,
                                Long.MAX_VALUE,
                                "count"));
        assertThrows(
                IOException.class, () -> StateStore.exact(Double.NaN, 0, Long.MAX_VALUE, "count"));
        assertThrows(
                IOException.class,
                () -> StateStore.exact(Double.POSITIVE_INFINITY, 0, Long.MAX_VALUE, "count"));
        assertThrows(IOException.class, () -> StateStore.exact("10", 0, 100, "amount"));
        assertThrows(IOException.class, () -> StateStore.exact(null, 0, 100, "amount"));
        assertThrows(IOException.class, () -> StateStore.exact(-1, 0, 100, "amount"));
    }
}
