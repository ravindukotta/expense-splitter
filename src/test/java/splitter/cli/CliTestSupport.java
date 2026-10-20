package splitter.cli;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.io.TempDir;

abstract class CliTestSupport {
    record Result(int exitCode, String out, String err) {
    }

    @TempDir
    Path dir;

    Result run(String stdin, String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        String[] full = new String[args.length + 2];
        full[0] = "--file";
        full[1] = dir.resolve("data.json").toString();
        System.arraycopy(args, 0, full, 2, args.length);
        int code = App.execute(full, new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8),
                LocalDate.of(2026, 10, 19));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }
}
