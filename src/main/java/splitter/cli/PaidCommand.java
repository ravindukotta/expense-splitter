package splitter.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import splitter.domain.Ledger;
import splitter.domain.Money;
import splitter.domain.Payment;

@Command(name = "paid", description = "Record that one member paid another the suggested amount.")
final class PaidCommand implements Callable<Integer> {
    @ParentCommand
    private SplitCommand parent;
    @Parameters(index = "0", paramLabel = "<from>")
    private String from;
    @Parameters(index = "1", paramLabel = "<to>")
    private String to;

    @Override
    public Integer call() {
        Context ctx = parent.context();
        Ledger ledger = ctx.store().load();
        Payment p = ledger.recordPayment(from, to, ctx.today());
        ctx.store().save(ledger);
        ctx.out().print("Recorded payment #" + p.id() + ": " + p.from() + " paid " + p.to() + " "
                + Money.format(p.amount()) + "\n");
        return 0;
    }
}
