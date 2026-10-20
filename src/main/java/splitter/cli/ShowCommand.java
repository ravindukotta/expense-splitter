package splitter.cli;

import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import splitter.domain.Expense;
import splitter.domain.Ledger;
import splitter.domain.Money;
import splitter.domain.Split;

@Command(name = "show", description = "Show one expense.")
final class ShowCommand implements Callable<Integer> {
    @ParentCommand
    private SplitCommand parent;
    @Parameters(index = "0", paramLabel = "<id>")
    private int id;

    @Override
    public Integer call() {
        Context ctx = parent.context();
        Ledger ledger = ctx.store().load();
        Expense e = ledger.expense(id);
        String splitText = switch (e.split()) {
            case Split.Equal eq -> "equally";
            case Split.Percent p -> p.percentages().entrySet().stream()
                    .map(x -> x.getKey() + " " + Money.format(x.getValue()) + "%")
                    .collect(Collectors.joining(", ", "by percentage (", ")"));
        };
        ctx.out().print(Format.expenseHeadline("Expense", e) + "\n"
                + "  Date: " + e.date() + "\n"
                + "  Split: " + splitText + "\n"
                + Format.sharesLine(e, ledger.members().order()) + "\n");
        return 0;
    }
}
