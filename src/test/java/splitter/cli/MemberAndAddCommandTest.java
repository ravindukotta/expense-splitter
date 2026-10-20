package splitter.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemberAndAddCommandTest extends CliTestSupport {
    @BeforeEach void flat() { for (var n : List.of("Alex", "Sam", "Priya", "Jo")) run("", "member", "add", n); }

    @Test void addEqualPrintsHeadlineAndShares() {
        var r = run("", "add", "Movie tickets", "36", "--paid-by", "Alex", "--with", "Alex,Sam,Priya");
        assertEquals(0, r.exitCode());
        assertEquals("Added #1 Movie tickets 36.00 paid by Alex\n  Alex 12.00 · Sam 12.00 · Priya 12.00\n", r.out());
    }
    @Test void addPercent() {
        var r = run("", "add", "Rent Oct", "1200", "--paid-by", "Sam", "--split", "Alex=40,Sam=30,Priya=30");
        assertEquals("Added #1 Rent Oct 1200.00 paid by Sam\n  Alex 480.00 · Sam 360.00 · Priya 360.00\n", r.out());
    }
    @Test void errorsGoToStderrAndChangeNothing() {
        var r = run("", "add", "x", "12.345", "--paid-by", "Alex");
        assertNotEquals(0, r.exitCode());
        assertTrue(r.err().startsWith("Error: Invalid amount '12.345'"));
        assertEquals("No expenses yet.\n", run("", "list").out());
    }
    @Test void withAndSplitTogetherRejected() {
        var r = run("", "add", "x", "10", "--paid-by", "Alex", "--with", "Alex", "--split", "Alex=100");
        assertEquals("Error: Use either --with or --split, not both\n", r.err());
    }
    @Test void listShowsExpenses() {
        run("", "add", "Groceries", "60", "--paid-by", "Alex");
        assertTrue(run("", "list").out().matches("(?s)\\s*#1\\s+2026-10-19\\s+Groceries\\s+60\\.00\\s+Alex\\s*"));
    }
    @Test void membersListsActiveOnly() {
        run("", "member", "remove", "Jo");
        assertEquals("Alex\nSam\nPriya\n", run("", "members").out());
    }
    @Test void showUnknownIdFails() {
        assertEquals("Error: No expense #5\n", run("", "show", "5").err());
    }
    @Test void showPrintsDetails() {
        run("", "add", "Rent", "100", "--paid-by", "Sam", "--split", "Alex=40,Sam=60");
        assertEquals("Expense #1 Rent 100.00 paid by Sam\n  Date: 2026-10-19\n"
                + "  Split: by percentage (Alex 40.00%, Sam 60.00%)\n  Alex 40.00 · Sam 60.00\n",
                run("", "show", "1").out());
    }
}
