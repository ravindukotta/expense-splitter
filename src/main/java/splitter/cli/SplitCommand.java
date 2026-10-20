package splitter.cli;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDate;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import splitter.storage.JsonStore;

@Command(name = "split", mixinStandardHelpOptions = true, description = "Split shared expenses in a flat.",
        subcommands = {MemberCommand.class, MembersCommand.class, AddCommand.class, ListCommand.class,
                ShowCommand.class, EditCommand.class, DeleteCommand.class, OwesCommand.class,
                PaidCommand.class, PaymentsCommand.class, UnpayCommand.class})
public final class SplitCommand {
    @Option(names = "--file", description = "Data file (default: ~/.splitter/data.json)")
    private Path file;

    private final BufferedReader in;
    private final PrintStream out;
    private final LocalDate today;

    SplitCommand(BufferedReader in, PrintStream out, LocalDate today) {
        this.in = in;
        this.out = out;
        this.today = today;
    }

    Context context() {
        return new Context(new JsonStore(file != null ? file : JsonStore.defaultPath()), in, out, today);
    }
}
