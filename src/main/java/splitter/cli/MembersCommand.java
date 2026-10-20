package splitter.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

@Command(name = "members", description = "List active members.")
final class MembersCommand implements Callable<Integer> {
    @ParentCommand
    private SplitCommand parent;

    @Override
    public Integer call() {
        Context ctx = parent.context();
        var names = ctx.store().load().members().activeNames();
        if (names.isEmpty()) {
            ctx.out().print("No members yet.\n");
        } else {
            ctx.out().print(String.join("\n", names) + "\n");
        }
        return 0;
    }
}
