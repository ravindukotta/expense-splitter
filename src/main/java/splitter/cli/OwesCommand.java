package splitter.cli;

import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;
import splitter.domain.Money;
import splitter.settlement.Transfer;

@Command(name = "owes", description = "Show who should pay whom to settle up.")
final class OwesCommand implements Callable<Integer> {
    @ParentCommand
    private SplitCommand parent;

    @Override
    public Integer call() {
        Context ctx = parent.context();
        List<Transfer> transfers = ctx.store().load().suggestedPayments();
        if (transfers.isEmpty()) {
            ctx.out().print("All settled up.\n");
            return 0;
        }
        int fromWidth = 0;
        int toWidth = 0;
        for (Transfer t : transfers) {
            fromWidth = Math.max(fromWidth, t.from().length());
            toWidth = Math.max(toWidth, t.to().length());
        }
        var sb = new StringBuilder();
        for (Transfer t : transfers) {
            sb.append(String.format("  %-" + fromWidth + "s pays %-" + toWidth + "s  %s\n",
                    t.from(), t.to(), Money.format(t.amount())));
        }
        ctx.out().print(sb);
        return 0;
    }
}
