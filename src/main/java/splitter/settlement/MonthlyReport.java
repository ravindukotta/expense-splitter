package splitter.settlement;

import splitter.domain.Expense;
import splitter.domain.Payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public record MonthlyReport(YearMonth month, int expenseCount, BigDecimal total, List<Row> rows,
                            List<Transfer> settleUp) {

    /** All values are scale 2. Earlier and balance are positive when the member is owed money. */
    public record Row(String name, BigDecimal paid, BigDecimal share, BigDecimal earlier, BigDecimal balance) {
        private boolean hasValue() {
            return paid.signum() != 0 || share.signum() != 0 || earlier.signum() != 0 || balance.signum() != 0;
        }
    }

    public MonthlyReport {
        rows = List.copyOf(rows);
        settleUp = List.copyOf(settleUp);
    }

    public static MonthlyReport of(List<Expense> expenses, List<Payment> payments,
                                   YearMonth month, List<String> memberOrder) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        var before = expenses.stream().filter(e -> e.date().isBefore(first)).toList();
        var inMonth = expenses.stream()
            .filter(e -> !e.date().isBefore(first) && !e.date().isAfter(last)).toList();
        var settled = payments.stream().filter(p -> !p.date().isAfter(last)).toList();

        var earlier = Balances.compute(before, settled, memberOrder);
        var paid = new LinkedHashMap<String, BigDecimal>();
        var share = new LinkedHashMap<String, BigDecimal>();
        for (String name : memberOrder) {
            paid.put(name, BigDecimal.ZERO.setScale(2));
            share.put(name, BigDecimal.ZERO.setScale(2));
        }
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (Expense e : inMonth) {
            total = total.add(e.amount());
            paid.merge(e.paidBy(), e.amount(), BigDecimal::add);
            e.shares(memberOrder).forEach((name, s) -> share.merge(name, s, BigDecimal::add));
        }

        var balances = new LinkedHashMap<String, BigDecimal>();
        var rows = new ArrayList<Row>();
        for (String name : memberOrder) {
            BigDecimal balance = earlier.get(name).add(paid.get(name)).subtract(share.get(name));
            balances.put(name, balance);
            var row = new Row(name, paid.get(name), share.get(name), earlier.get(name), balance);
            if (row.hasValue()) {
                rows.add(row);
            }
        }
        return new MonthlyReport(month, inMonth.size(), total.setScale(2), rows, Settlement.simplify(balances));
    }

    public boolean nothingToReport() {
        return expenseCount == 0 && settleUp.isEmpty();
    }
}
