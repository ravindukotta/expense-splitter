package splitter.settlement;

import org.junit.jupiter.api.Test;
import splitter.domain.Expense;
import splitter.domain.Payment;
import splitter.domain.Split;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BalancesTest {
    static final List<String> ORDER = List.of("Alex", "Sam", "Priya", "Jo");
    static final LocalDate D = LocalDate.of(2026, 10, 19);

    @Test void payerIsCreditedAndSharersDebited() {
        var e = new Expense(1, "Movie tickets", new BigDecimal("36.00"), "Alex",
            new Split.Equal(List.of("Alex", "Sam", "Priya")), D);
        var b = Balances.compute(List.of(e), List.of(), ORDER);
        assertEquals(List.of("Alex", "Sam", "Priya", "Jo"), List.copyOf(b.keySet()));
        assertEquals(new BigDecimal("24.00"), b.get("Alex"));
        assertEquals(new BigDecimal("-12.00"), b.get("Sam"));
        assertEquals(new BigDecimal("0.00"), b.get("Jo"));
    }

    @Test void paymentMovesBalanceTowardZero() {
        var e = new Expense(1, "x", new BigDecimal("20.00"), "Alex", new Split.Equal(List.of("Jo")), D);
        var p = new Payment(1, "Jo", "Alex", new BigDecimal("20.00"), D);
        var b = Balances.compute(List.of(e), List.of(p), ORDER);
        assertEquals(0, b.get("Alex").signum());
        assertEquals(0, b.get("Jo").signum());
    }
}
