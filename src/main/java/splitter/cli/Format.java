package splitter.cli;

import java.util.List;
import java.util.stream.Collectors;
import splitter.domain.Expense;
import splitter.domain.Money;

final class Format {
    private Format() {
    }

    static String expenseHeadline(String verb, Expense e) {
        return verb + " #" + e.id() + " " + e.description() + " " + Money.format(e.amount())
                + " paid by " + e.paidBy();
    }

    static String sharesLine(Expense e, List<String> order) {
        return e.shares(order).entrySet().stream()
                .map(s -> s.getKey() + " " + Money.format(s.getValue()))
                .collect(Collectors.joining(" · ", "  ", ""));
    }
}
