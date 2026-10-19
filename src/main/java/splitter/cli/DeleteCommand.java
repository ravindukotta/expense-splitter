package splitter.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import splitter.domain.Expense;
import splitter.domain.Ledger;
import splitter.domain.Money;

@Command(name = "delete", description = "Delete an expense.")
final class DeleteCommand implements Callable<Integer> {
    @ParentCommand
    private SplitCommand parent;
    @Parameters(index = "0", paramLabel = "<id>")
    private int id;

    @Override
    public Integer call() {
        Context ctx = parent.context();
        Ledger ledger = ctx.store().load();
        Expense e = ledger.expense(id);
        if (!ctx.confirm("Delete #" + e.id() + " " + e.description() + " " + Money.format(e.amount()) + "? [y/N]: ")) {
            ctx.out().print("Cancelled\n");
            return 0;
        }
        ledger.deleteExpense(id);
        ctx.store().save(ledger);
        ctx.out().print("Deleted #" + id + "\n");
        return 0;
    }
}
