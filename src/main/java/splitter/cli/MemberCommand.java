package splitter.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import splitter.domain.Ledger;

@Command(name = "member", description = "Add or remove flat members.",
        subcommands = {MemberCommand.Add.class, MemberCommand.Remove.class})
final class MemberCommand {
    @ParentCommand
    private SplitCommand parent;

    @Command(name = "add", description = "Add a member (or re-activate a removed one).")
    static final class Add implements Callable<Integer> {
        @ParentCommand
        private MemberCommand member;
        @Parameters(index = "0", paramLabel = "<name>")
        private String name;

        @Override
        public Integer call() {
            Context ctx = member.parent.context();
            Ledger ledger = ctx.store().load();
            String added = ledger.addMember(name).name();
            ctx.store().save(ledger);
            ctx.out().print("Added member " + added + "\n");
            return 0;
        }
    }

    @Command(name = "remove", description = "Remove a member whose balance is zero.")
    static final class Remove implements Callable<Integer> {
        @ParentCommand
        private MemberCommand member;
        @Parameters(index = "0", paramLabel = "<name>")
        private String name;

        @Override
        public Integer call() {
            Context ctx = member.parent.context();
            Ledger ledger = ctx.store().load();
            String canonical = ledger.members().resolveActive(name);
            ledger.removeMember(canonical);
            ctx.store().save(ledger);
            ctx.out().print("Removed member " + canonical + "\n");
            return 0;
        }
    }
}
