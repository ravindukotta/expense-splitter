package splitter.cli;

import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;
import splitter.domain.Money;
import splitter.domain.Payment;

@Command(name = "payments", description = "List recorded payments.")
final class PaymentsCommand implements Callable<Integer> {
    @ParentCommand
    private SplitCommand parent;

    @Override
    public Integer call() {
        Context ctx = parent.context();
        List<Payment> payments = ctx.store().load().payments();
        if (payments.isEmpty()) {
            ctx.out().print("No payments yet.\n");
            return 0;
        }
        var sb = new StringBuilder();
        for (Payment p : payments) {
            sb.append(" #").append(p.id()).append("  ").append(p.date()).append("  ")
                    .append(p.from()).append(" paid ").append(p.to()).append(' ')
                    .append(Money.format(p.amount())).append('\n');
        }
        ctx.out().print(sb);
        return 0;
    }
}
