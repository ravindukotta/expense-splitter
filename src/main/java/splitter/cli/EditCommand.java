package splitter.cli;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import splitter.domain.Expense;
import splitter.domain.ExpenseEdit;
import splitter.domain.Ledger;
import splitter.domain.Money;
import splitter.domain.SplitSpec;
import splitter.domain.SplitterException;

@Command(name = "edit", description = "Change fields of an expense.")
final class EditCommand implements Callable<Integer> {
    @ParentCommand
    private SplitCommand parent;
    @Parameters(index = "0", paramLabel = "<id>")
    private int id;
    @Option(names = "--description", description = "New description")
    private String description;
    @Option(names = "--amount", description = "New amount")
    private String amount;
    @Option(names = "--paid-by", description = "Who paid")
    private String paidBy;
    @Option(names = "--with", description = "Comma-separated names, or 'everyone'")
    private String with;
    @Option(names = "--split", description = "Percentages, e.g. Alex=40,Sam=60")
    private String split;

    @Override
    public Integer call() {
        if (with != null && split != null) {
            throw new SplitterException("Use either --with or --split, not both");
        }
        if (description == null && amount == null && paidBy == null && with == null && split == null) {
            throw new SplitterException("Nothing to change");
        }
        Optional<SplitSpec> spec = split != null ? Optional.of(Args.split(split))
                : with != null ? Optional.of(Args.with(with)) : Optional.empty();
        Optional<BigDecimal> newAmount = Optional.ofNullable(amount).map(Money::parseAmount);
        Context ctx = parent.context();
        Ledger ledger = ctx.store().load();
        Expense e = ledger.editExpense(id,
                new ExpenseEdit(Optional.ofNullable(description), newAmount, Optional.ofNullable(paidBy), spec));
        ctx.store().save(ledger);
        ctx.out().print(Format.expenseHeadline("Updated", e) + "\n"
                + Format.sharesLine(e, ledger.members().order()) + "\n");
        return 0;
    }
}
