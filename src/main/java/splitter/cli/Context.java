package splitter.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import splitter.storage.JsonStore;

/** Everything a command needs from its environment. */
public record Context(JsonStore store, BufferedReader in, PrintStream out, LocalDate today) {
    /** Prints the prompt, reads one line, and returns true only for y/yes; EOF counts as no. */
    public boolean confirm(String prompt) {
        out.print(prompt);
        out.flush();
        try {
            String line = in.readLine();
            return line != null && (line.trim().equalsIgnoreCase("y") || line.trim().equalsIgnoreCase("yes"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
