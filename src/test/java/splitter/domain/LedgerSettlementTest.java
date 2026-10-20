package splitter.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import splitter.settlement.Transfer;

class LedgerSettlementTest {
    static final LocalDate D = LocalDate.of(2026, 10, 19);
    Ledger ledger;

    @BeforeEach
    void flat() {
        ledger = new Ledger();
        for (var n : List.of("Alex", "Sam", "Priya", "Jo")) ledger.addMember(n);
    }

    @Test void chainSuggestsOnePaymentAndPayingClearsIt() {
        ledger.addExpense("a", new BigDecimal("20.00"), "Sam", new SplitSpec.Equal(List.of("Jo")), D);
        ledger.addExpense("b", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Sam")), D);
        assertEquals(List.of(new Transfer("Jo", "Alex", new BigDecimal("20.00"))), ledger.suggestedPayments());
        var p = ledger.recordPayment("jo", "ALEX", D);
        assertEquals(new Payment(1, "Jo", "Alex", new BigDecimal("20.00"), D), p);
        assertEquals(List.of(), ledger.suggestedPayments());
    }
    @Test void payingWrongDirectionOrNonexistentIsAnError() {
        ledger.addExpense("a", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Jo")), D);
        assertEquals("No suggested payment from Alex to Jo",
            assertThrows(SplitterException.class, () -> ledger.recordPayment("Alex", "Jo", D)).getMessage());
        assertThrows(SplitterException.class, () -> ledger.recordPayment("Sam", "Alex", D));
    }
    @Test void unpayRestoresTheSuggestion() {
        ledger.addExpense("a", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Jo")), D);
        var p = ledger.recordPayment("Jo", "Alex", D);
        ledger.unpay(p.id());
        assertEquals(1, ledger.suggestedPayments().size());
        assertEquals("No payment #7", assertThrows(SplitterException.class, () -> ledger.unpay(7)).getMessage());
    }
    @Test void editingAfterPaymentSuggestsRefund() {
        var e = ledger.addExpense("a", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Jo")), D);
        ledger.recordPayment("Jo", "Alex", D);
        ledger.editExpense(e.id(), new ExpenseEdit(Optional.empty(), Optional.of(new BigDecimal("15.00")), Optional.empty(), Optional.empty()));
        assertEquals(List.of(new Transfer("Alex", "Jo", new BigDecimal("5.00"))), ledger.suggestedPayments());
        assertEquals(1, ledger.payments().size());
    }
    @Test void cannotRemoveMemberWithBalance() {
        ledger.addExpense("a", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Jo")), D);
        var ex = assertThrows(SplitterException.class, () -> ledger.removeMember("Jo"));
        assertEquals("Jo still has a balance of -20.00; settle up first", ex.getMessage());
    }
    @Test void removedMemberLeftOutOfEveryoneAndNewExpenses() {
        ledger.removeMember("Jo");
        var e = ledger.addExpense("x", new BigDecimal("3.00"), "Alex", new SplitSpec.Everyone(), D);
        assertEquals(List.of("Alex", "Sam", "Priya"), e.split().people());
        assertThrows(SplitterException.class, () -> ledger.addExpense("y", new BigDecimal("1.00"), "Jo", new SplitSpec.Everyone(), D));
    }
    @Test void oldExpenseWithRemovedMemberStaysEditableAndTheyCanSettle() {
        var e = ledger.addExpense("a", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Jo")), D);
        ledger.recordPayment("Jo", "Alex", D);
        ledger.removeMember("Jo");
        ledger.editExpense(e.id(), new ExpenseEdit(Optional.empty(), Optional.of(new BigDecimal("30.00")), Optional.empty(), Optional.empty()));
        assertEquals(List.of(new Transfer("Jo", "Alex", new BigDecimal("10.00"))), ledger.suggestedPayments());
        ledger.recordPayment("Jo", "Alex", D);
        assertEquals(List.of(), ledger.suggestedPayments());
    }
    @Test void editCannotAddRemovedMemberAsNewParticipant() {
        ledger.removeMember("Jo");
        var e = ledger.addExpense("a", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Sam")), D);
        assertThrows(SplitterException.class, () -> ledger.editExpense(e.id(), new ExpenseEdit(Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(new SplitSpec.Equal(List.of("Sam", "Jo"))))));
    }
    @Test void snapshotRestoreRoundTripKeepsCounters() {
        var e = ledger.addExpense("a", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Jo")), D);
        ledger.deleteExpense(e.id());
        var copy = Ledger.restore(ledger.snapshot());
        assertEquals(2, copy.addExpense("b", new BigDecimal("1.00"), "Alex", new SplitSpec.Everyone(), D).id());
        assertEquals(ledger.members().all(), copy.members().all());
    }
}
