package splitter.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import splitter.domain.Expense;
import splitter.domain.Ledger;
import splitter.domain.Money;
import splitter.domain.SplitSpec;
import splitter.domain.SplitterException;

@Command(name = "add", description = "Add an expense.")
final class AddCommand implements Callable<Integer> {
    @ParentCommand
    private SplitCommand parent;
    @Parameters(index = "0", paramLabel = "<description>")
    private String description;
    @Parameters(index = "1", paramLabel = "<amount>")
    private String amount;
    @Option(names = "--paid-by", required = true, description = "Who paid")
    private String paidBy;
    @Option(names = "--with", description = "Comma-separated names, or 'everyone' (default)")
    private String with;
    @Option(names = "--split", description = "Percentages, e.g. Alex=40,Sam=60")
    private String split;

    @Override
    public Integer call() {
        if (with != null && split != null) {
            throw new SplitterException("Use either --with or --split, not both");
        }
        SplitSpec spec = split != null ? Args.split(split) : with != null ? Args.with(with) : new SplitSpec.Everyone();
        Context ctx = parent.context();
        Ledger ledger = ctx.store().load();
        Expense e = ledger.addExpense(description, Money.parseAmount(amount), paidBy, spec, ctx.today());
        ctx.store().save(ledger);
        ctx.out().print(Format.expenseHeadline("Added", e) + "\n"
                + Format.sharesLine(e, ledger.members().order()) + "\n");
        return 0;
    }
}
