package splitter.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReportCommandTest extends CliTestSupport {
    @BeforeEach void flat() {
        for (var n : List.of("Alex", "Sam", "Priya", "Jo")) run("", "member", "add", n);
        run("", "add", "Movie tickets", "36", "--paid-by", "Alex", "--with", "Alex,Sam,Priya");
    }
    @Test void writes_this_months_report() throws IOException {
        Path out = dir.resolve("oct.html");
        var r = run("", "report", "--out", out.toString());
        assertEquals(0, r.exitCode());
        assertEquals("Wrote " + out + "\n", r.out());
        String html = Files.readString(out);
        assertTrue(html.contains("Flat expenses — October 2026"));
        assertTrue(html.contains("Sam pays Alex"));
        assertTrue(html.contains("Priya pays Alex"));
    }
    @Test void overwrites_existing_file() throws IOException {
        Path out = dir.resolve("r.html");
        Files.writeString(out, "old");
        run("", "report", "--out", out.toString());
        assertTrue(Files.readString(out).startsWith("<!DOCTYPE html>"));
    }
    @Test void carry_over_month_without_expenses() throws IOException {
        Path out = dir.resolve("nov.html");
        run("", "report", "--month", "2026-11", "--out", out.toString());
        assertTrue(Files.readString(out).contains("0 expenses · total 0.00"));
    }
    @Test void nothing_to_report_writes_no_file() {
        Path out = dir.resolve("sep.html");
        var r = run("", "report", "--month", "2026-09", "--out", out.toString());
        assertEquals("Nothing to report for September 2026.\n", r.out());
        assertFalse(Files.exists(out));
    }
    @Test void invalid_month() {
        var r = run("", "report", "--month", "2026-13", "--out", dir.resolve("x.html").toString());
        assertEquals("Error: Invalid month '2026-13'; expected YYYY-MM\n", r.err());
        assertFalse(Files.exists(dir.resolve("x.html")));
    }
    @Test void unwritable_out_is_a_clean_error() {
        Path out = dir.resolve("missing-folder/r.html");
        var r = run("", "report", "--out", out.toString());
        assertEquals(1, r.exitCode());
        assertTrue(r.err().startsWith("Error: Could not write report " + out + ":"));
    }
    @Test void does_not_change_the_data_file() throws IOException {
        byte[] before = Files.readAllBytes(dir.resolve("data.json"));
        run("", "report", "--out", dir.resolve("r.html").toString());
        assertArrayEquals(before, Files.readAllBytes(dir.resolve("data.json")));
    }
}
