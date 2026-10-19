package splitter.domain;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LedgerExpenseTest {
    static final LocalDate D = LocalDate.of(2026, 10, 19);
    Ledger ledger;

    @BeforeEach
    void flat() {
        ledger = new Ledger();
        for (var n : List.of("Alex", "Sam", "Priya", "Jo")) ledger.addMember(n);
    }

    static LinkedHashMap<String, BigDecimal> pct(String... nameValue) {
        var map = new LinkedHashMap<String, BigDecimal>();
        for (int i = 0; i < nameValue.length; i += 2) map.put(nameValue[i], new BigDecimal(nameValue[i + 1]));
        return map;
    }

    @Test void addsEqualSplitAmongNamedPeopleWithCanonicalNames() {
        var e = ledger.addExpense("Movie tickets", new BigDecimal("36.00"), "alex",
            new SplitSpec.Equal(List.of("alex", "SAM", "Priya")), D);
        assertEquals(1, e.id());
        assertEquals("Alex", e.paidBy());
        assertEquals(new Split.Equal(List.of("Alex", "Sam", "Priya")), e.split());
    }

    @Test void everyoneIsFrozenAtTimeOfAdding() {
        var e = ledger.addExpense("Broadband Sept", new BigDecimal("45.00"), "Priya", new SplitSpec.Everyone(), D);
        ledger.addMember("Kim");
        assertEquals(List.of("Alex", "Sam", "Priya", "Jo"), ledger.expense(e.id()).split().people());
    }

    @Test void percentSplit() {
        var e = ledger.addExpense("Rent Oct", new BigDecimal("1200.00"), "Sam",
            new SplitSpec.Percent(pct("Alex", "40", "Sam", "30", "Priya", "30")), D);
        assertEquals(new BigDecimal("480.00"), e.shares(ledger.members().order()).get("Alex"));
    }

    @Test void percentagesMustSumTo100() {
        var ex = assertThrows(SplitterException.class, () -> ledger.addExpense("Rent", new BigDecimal("100.00"), "Sam",
            new SplitSpec.Percent(pct("Alex", "40", "Sam", "30", "Priya", "29")), D));
        assertEquals("Percentages add up to 99.00, not 100", ex.getMessage());
    }

    @Test void rejectsDuplicatePeopleIgnoringCase() {
        var ex = assertThrows(SplitterException.class, () -> ledger.addExpense("x", new BigDecimal("10.00"), "Alex",
            new SplitSpec.Equal(List.of("Alex", "alex")), D));
        assertEquals("Alex is listed twice", ex.getMessage());
        assertThrows(SplitterException.class, () -> ledger.addExpense("x", new BigDecimal("10.00"), "Alex",
            new SplitSpec.Percent(pct("Sam", "50", "sam", "50")), D));
    }

    @Test void rejectsUnknownPayerAndBlankDescription() {
        assertThrows(SplitterException.class, () -> ledger.addExpense("x", new BigDecimal("1.00"), "Zed", new SplitSpec.Everyone(), D));
        assertThrows(SplitterException.class, () -> ledger.addExpense(" ", new BigDecimal("1.00"), "Alex", new SplitSpec.Everyone(), D));
    }

    @Test void payerNeedNotBeInvolved() {
        var e = ledger.addExpense("Gift", new BigDecimal("10.00"), "Alex", new SplitSpec.Equal(List.of("Sam")), D);
        assertEquals(List.of("Sam"), e.split().people());
    }

    @Test void idsAreNeverReused() {
        ledger.addExpense("a", new BigDecimal("1.00"), "Alex", new SplitSpec.Everyone(), D);
        var b = ledger.addExpense("b", new BigDecimal("1.00"), "Alex", new SplitSpec.Everyone(), D);
        ledger.deleteExpense(b.id());
        assertEquals(3, ledger.addExpense("c", new BigDecimal("1.00"), "Alex", new SplitSpec.Everyone(), D).id());
    }

    @Test void unknownIdErrors() {
        assertEquals("No expense #99", assertThrows(SplitterException.class, () -> ledger.deleteExpense(99)).getMessage());
        assertThrows(SplitterException.class, () -> ledger.editExpense(99, new ExpenseEdit(Optional.of("x"), Optional.empty(), Optional.empty(), Optional.empty())));
    }

    @Test void editAmountKeepsPercentages() {
        var e = ledger.addExpense("Rent", new BigDecimal("1000.00"), "Sam",
            new SplitSpec.Percent(pct("Alex", "40", "Sam", "60")), D);
        var u = ledger.editExpense(e.id(), new ExpenseEdit(Optional.empty(), Optional.of(new BigDecimal("2000.00")), Optional.empty(), Optional.empty()));
        assertInstanceOf(Split.Percent.class, u.split());
        assertEquals(new BigDecimal("800.00"), u.shares(ledger.members().order()).get("Alex"));
        assertEquals("Rent", u.description());
    }

    @Test void editWithOnPercentExpenseBecomesEqual() {
        var e = ledger.addExpense("Rent", new BigDecimal("1000.00"), "Sam",
            new SplitSpec.Percent(pct("Alex", "40", "Sam", "60")), D);
        var u = ledger.editExpense(e.id(), new ExpenseEdit(Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(new SplitSpec.Equal(List.of("Alex", "Sam")))));
        assertEquals(new Split.Equal(List.of("Alex", "Sam")), u.split());
    }

    @Test void rejectedEditLeavesExpenseUnchanged() {
        var e = ledger.addExpense("Rent", new BigDecimal("1000.00"), "Sam", new SplitSpec.Everyone(), D);
        assertThrows(SplitterException.class, () -> ledger.editExpense(e.id(), new ExpenseEdit(
            Optional.of("Changed"), Optional.empty(), Optional.of("Zed"), Optional.empty())));
        assertEquals(e, ledger.expense(e.id()));
    }

    @Test void storesAmountsAndPercentagesAtScaleTwoAndRejectsInvalidValues() {
        var e = ledger.addExpense("Rent", new BigDecimal("10"), "Sam",
            new SplitSpec.Percent(pct("Alex", "40", "Sam", "60")), D);
        assertEquals(new BigDecimal("10.00"), e.amount());
        assertEquals(new BigDecimal("40.00"), ((Split.Percent) e.split()).percentages().get("Alex"));
        assertThrows(SplitterException.class, () -> ledger.addExpense("x", BigDecimal.ZERO, "Sam", new SplitSpec.Everyone(), D));
        assertThrows(SplitterException.class, () -> ledger.addExpense("x", new BigDecimal("5"), "Sam",
            new SplitSpec.Percent(pct("Alex", "100", "Sam", "0")), D));
        assertThrows(SplitterException.class, () -> ledger.addExpense("x", new BigDecimal("5"), "Sam",
            new SplitSpec.Equal(List.of()), D));
    }

    @Test void storedSplitIsIndependentOfCallerCollections() {
        var map = pct("Alex", "50", "Sam", "50");
        var e = ledger.addExpense("Rent", new BigDecimal("10"), "Sam", new SplitSpec.Percent(map), D);
        map.put("Jo", new BigDecimal("5"));
        assertEquals(List.of("Alex", "Sam"), ledger.expense(e.id()).split().people());
    }
}
