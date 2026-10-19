package splitter.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import splitter.domain.Ledger;
import splitter.domain.Money;
import splitter.domain.Payment;
import splitter.domain.SplitterException;

@Command(name = "unpay", description = "Undo a recorded payment.")
final class UnpayCommand implements Callable<Integer> {
    @ParentCommand
    private SplitCommand parent;
    @Parameters(index = "0", paramLabel = "<id>")
    private int id;

    @Override
    public Integer call() {
        Context ctx = parent.context();
        Ledger ledger = ctx.store().load();
        Payment p = ledger.payments().stream().filter(x -> x.id() == id).findFirst()
                .orElseThrow(() -> new SplitterException("No payment #" + id));
        if (!ctx.confirm("Undo payment #" + p.id() + ": " + p.from() + " paid " + p.to() + " "
                + Money.format(p.amount()) + "? [y/N]: ")) {
            ctx.out().print("Cancelled\n");
            return 0;
        }
        ledger.unpay(id);
        ctx.store().save(ledger);
        ctx.out().print("Removed payment #" + id + "\n");
        return 0;
    }
}
