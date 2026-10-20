package splitter.cli;

import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;
import splitter.domain.Expense;
import splitter.domain.Money;

@Command(name = "list", description = "List all expenses.")
final class ListCommand implements Callable<Integer> {
    @ParentCommand
    private SplitCommand parent;

    @Override
    public Integer call() {
        Context ctx = parent.context();
        List<Expense> expenses = ctx.store().load().expenses();
        if (expenses.isEmpty()) {
            ctx.out().print("No expenses yet.\n");
            return 0;
        }
        int idWidth = 0;
        int descWidth = 0;
        int amountWidth = 0;
        for (Expense e : expenses) {
            idWidth = Math.max(idWidth, String.valueOf(e.id()).length());
            descWidth = Math.max(descWidth, e.description().length());
            amountWidth = Math.max(amountWidth, Money.format(e.amount()).length());
        }
        var sb = new StringBuilder();
        for (Expense e : expenses) {
            sb.append(String.format(" #%-" + idWidth + "d  %s  %-" + descWidth + "s  %" + amountWidth + "s  %s\n",
                    e.id(), e.date(), e.description(), Money.format(e.amount()), e.paidBy()));
        }
        ctx.out().print(sb);
        return 0;
    }
}
