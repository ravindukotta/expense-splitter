package splitter.cli;

import java.io.BufferedReader;
import java.io.PrintStream;
import java.time.LocalDate;
import splitter.storage.JsonStore;

/** Everything a command needs from its environment. */
public record Context(JsonStore store, BufferedReader in, PrintStream out, LocalDate today) {
}
