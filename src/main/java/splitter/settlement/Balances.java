package splitter.settlement;

import splitter.domain.Expense;
import splitter.domain.Payment;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

public final class Balances {
    private Balances() {
    }

    /** Positive means the member is owed money: paid - shares + payments made - payments received. */
    public static LinkedHashMap<String, BigDecimal> compute(
            List<Expense> expenses, List<Payment> payments, List<String> memberOrder) {
        var balances = new LinkedHashMap<String, BigDecimal>();
        for (String name : memberOrder) {
            balances.put(name, BigDecimal.ZERO.setScale(2));
        }
        for (Expense e : expenses) {
            balances.merge(e.paidBy(), e.amount(), BigDecimal::add);
            e.shares(memberOrder).forEach((name, share) -> balances.merge(name, share, BigDecimal::subtract));
        }
        for (Payment p : payments) {
            balances.merge(p.from(), p.amount(), BigDecimal::add);
            balances.merge(p.to(), p.amount(), BigDecimal::subtract);
        }
        balances.replaceAll((name, value) -> value.setScale(2));
        return balances;
    }
}
