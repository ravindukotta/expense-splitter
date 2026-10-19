package splitter.cli;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import picocli.CommandLine;
import splitter.domain.SplitterException;

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        var out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        var err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        System.exit(execute(args, System.in, out, err, LocalDate.now()));
    }

    static int execute(String[] args, InputStream in, PrintStream out, PrintStream err, LocalDate today) {
        var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        var root = new SplitCommand(reader, out, today);
        var commandLine = new CommandLine(root);
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        commandLine.setExecutionExceptionHandler((ex, cmd, parseResult) -> {
            if (ex instanceof SplitterException) {
                err.print("Error: " + ex.getMessage() + "\n");
                err.flush();
                return 1;
            }
            throw ex;
        });
        return commandLine.execute(args);
    }
}
