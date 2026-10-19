package splitter.domain;

import java.util.List;

public record LedgerData(
        List<Member> members,
        List<Expense> expenses,
        List<Payment> payments,
        int nextExpenseId,
        int nextPaymentId) {
}
