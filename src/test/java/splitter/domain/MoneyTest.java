package splitter.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {
    @Test void parsesWholeAndDecimalAmountsToScaleTwo() {
        assertEquals(new BigDecimal("36.00"), Money.parseAmount("36"));
        assertEquals(new BigDecimal("12.50"), Money.parseAmount("12.5"));
        assertEquals(new BigDecimal("0.01"), Money.parseAmount("0.01"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5", "12.345", "12,50", "1e3", ".5", "12.", " 12", "abc", ""})
    void rejectsMalformedAmountsQuotingTheInput(String text) {
        var ex = assertThrows(SplitterException.class, () -> Money.parseAmount(text));
        assertTrue(ex.getMessage().contains("'" + text + "'"));
    }

    @Test void parsesPercentages() {
        assertEquals(new BigDecimal("33.33"), Money.parsePercent("33.33"));
        assertThrows(SplitterException.class, () -> Money.parsePercent("0"));
        assertThrows(SplitterException.class, () -> Money.parsePercent("10.001"));
    }

    @Test void formatsWithTwoDecimals() {
        assertEquals("36.00", Money.format(new BigDecimal("36")));
        assertEquals("0.50", Money.format(new BigDecimal("0.5")));
    }
}
