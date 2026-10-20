package splitter.settlement;

import org.junit.jupiter.api.Test;
import splitter.domain.Expense;
import splitter.domain.Payment;
import splitter.domain.Split;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonthlyReportTest {
    static final List<String> ORDER = List.of("Alex", "Sam", "Priya", "Jo");
    static final YearMonth OCT = YearMonth.of(2026, 10);

    static MonthlyReport.Row row(String name, String paid, String share, String earlier, String balance) {
        return new MonthlyReport.Row(name, new BigDecimal(paid), new BigDecimal(share),
            new BigDecimal(earlier), new BigDecimal(balance));
    }

    static Expense expense(int id, String amount, String paidBy, Split split, LocalDate date) {
        return new Expense(id, "e" + id, new BigDecimal(amount), paidBy, split, date);
    }

    static List<Expense> octoberExpenses() {
        var rent = new LinkedHashMap<String, BigDecimal>();
        rent.put("Alex", new BigDecimal("40.00"));
        rent.put("Sam", new BigDecimal("30.00"));
        rent.put("Priya", new BigDecimal("30.00"));
        return List.of(
            expense(1, "1200.00", "Sam", new Split.Percent(rent), LocalDate.of(2026, 10, 1)),
            expense(2, "45.00", "Priya", new Split.Equal(ORDER), LocalDate.of(2026, 10, 3)),
            expense(3, "36.00", "Alex", new Split.Equal(List.of("Alex", "Sam", "Priya")), LocalDate.of(2026, 10, 11)),
            expense(4, "60.00", "Jo", new Split.Equal(ORDER), LocalDate.of(2026, 10, 18)));
    }

    static List<Expense> withSeptDebt(List<Expense> october) {
        var all = new ArrayList<Expense>();
        all.add(expense(5, "20.00", "Jo", new Split.Equal(List.of("Priya")), LocalDate.of(2026, 9, 15)));
        all.addAll(october);
        return all;
    }

    @Test void spec_example_with_carry_over() {
        var r = MonthlyReport.of(withSeptDebt(octoberExpenses()), List.of(), OCT, ORDER);
        assertEquals(4, r.expenseCount());
        assertEquals(new BigDecimal("1341.00"), r.total());
        assertEquals(List.of(
            row("Alex", "36.00", "518.25", "0.00", "-482.25"),
            row("Sam", "1200.00", "398.25", "0.00", "801.75"),
            row("Priya", "45.00", "398.25", "-20.00", "-373.25"),
            row("Jo", "60.00", "26.25", "20.00", "53.75")), r.rows());
        assertEquals(List.of(
            new Transfer("Alex", "Sam", new BigDecimal("482.25")),
            new Transfer("Priya", "Sam", new BigDecimal("319.50")),
            new Transfer("Priya", "Jo", new BigDecimal("53.75"))), r.settleUp());
    }

    @Test void payment_during_month_is_folded_into_earlier() {
        var pay = new Payment(1, "Priya", "Jo", new BigDecimal("20.00"), LocalDate.of(2026, 10, 5));
        var r = MonthlyReport.of(withSeptDebt(octoberExpenses()), List.of(pay), OCT, ORDER);
        assertEquals(row("Priya", "45.00", "398.25", "0.00", "-353.25"), r.rows().get(2));
        assertEquals(new Transfer("Priya", "Jo", new BigDecimal("33.75")), r.settleUp().get(2));
    }

    @Test void anything_after_the_month_is_ignored() {
        var later = new ArrayList<>(octoberExpenses());
        later.add(expense(9, "500.00", "Alex", new Split.Equal(ORDER), LocalDate.of(2026, 11, 1)));
        var pay = new Payment(1, "Alex", "Sam", new BigDecimal("482.25"), LocalDate.of(2026, 11, 2));
        assertEquals(MonthlyReport.of(octoberExpenses(), List.of(), OCT, ORDER),
                     MonthlyReport.of(later, List.of(pay), OCT, ORDER));
    }

    @Test void carry_over_without_expenses_still_reports() {
        var r = MonthlyReport.of(octoberExpenses(), List.of(), YearMonth.of(2026, 11), ORDER);
        assertEquals(0, r.expenseCount());
        assertEquals(new BigDecimal("0.00"), r.total());
        assertEquals(row("Alex", "0.00", "0.00", "-482.25", "-482.25"), r.rows().get(0));
        assertFalse(r.nothingToReport());
    }

    @Test void members_with_all_zero_are_left_out() {
        var r = MonthlyReport.of(List.of(expense(1, "10.00", "Alex", new Split.Equal(List.of("Sam")),
            LocalDate.of(2026, 10, 2))), List.of(), OCT, ORDER);
        assertEquals(List.of("Alex", "Sam"), r.rows().stream().map(MonthlyReport.Row::name).toList());
    }

    @Test void empty_month_with_nothing_owed_is_nothing_to_report() {
        var r = MonthlyReport.of(List.of(), List.of(), OCT, ORDER);
        assertTrue(r.nothingToReport());
        assertEquals(List.of(), r.rows());
    }
}
