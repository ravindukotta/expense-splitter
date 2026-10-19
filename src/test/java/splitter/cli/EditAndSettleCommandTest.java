package splitter.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EditAndSettleCommandTest extends CliTestSupport {
    @BeforeEach void flat() {
        for (var n : List.of("Alex", "Sam", "Priya", "Jo")) run("", "member", "add", n);
        run("", "add", "Movie tickets", "36", "--paid-by", "Alex", "--with", "Alex,Sam,Priya");
    }
    @Test void editChangesOnlyNamedFields() {
        var r = run("", "edit", "1", "--amount", "48", "--with", "everyone");
        assertEquals("Updated #1 Movie tickets 48.00 paid by Alex\n  Alex 12.00 · Sam 12.00 · Priya 12.00 · Jo 12.00\n", r.out());
    }
    @Test void editWithNothingFails() { assertEquals("Error: Nothing to change\n", run("", "edit", "1").err()); }
    @Test void editWithAndSplitTogetherFails() {
        assertEquals("Error: Use either --with or --split, not both\n",
                run("", "edit", "1", "--with", "Sam", "--split", "Sam=100").err());
    }
    @Test void deleteAsksAndRespectsAnswer() {
        assertTrue(run("n\n", "delete", "1").out().endsWith("Cancelled\n"));
        var r = run("y\n", "delete", "1");
        assertEquals("Delete #1 Movie tickets 36.00? [y/N]: Deleted #1\n", r.out());
        assertEquals("No expenses yet.\n", run("", "list").out());
    }
    @Test void deleteOnEofCancels() {
        assertTrue(run("", "delete", "1").out().endsWith("Cancelled\n"));
        assertTrue(run("", "show", "1").out().startsWith("Expense #1"));
    }
    @Test void deleteUnknownIdFailsWithoutPrompting() {
        var r = run("y\n", "delete", "9");
        assertEquals("Error: No expense #9\n", r.err());
        assertEquals("", r.out());
    }
    @Test void owesPaidAndUnpayCycle() {
        assertTrue(run("", "owes").out().matches("(?s)\\s*Sam\\s+pays Alex\\s+12\\.00\\s*\\n\\s*Priya\\s+pays Alex\\s+12\\.00\\s*"));
        assertEquals("Recorded payment #1: Sam paid Alex 12.00\n", run("", "paid", "sam", "alex").out());
        assertTrue(run("", "payments").out().contains("#1  2026-10-19  Sam paid Alex 12.00"));
        var r = run("y\n", "unpay", "1");
        assertEquals("Undo payment #1: Sam paid Alex 12.00? [y/N]: Removed payment #1\n", r.out());
        assertEquals("No payments yet.\n", run("", "payments").out());
    }
    @Test void unpayCancelAndUnknown() {
        run("", "paid", "Sam", "Alex");
        assertTrue(run("n\n", "unpay", "1").out().endsWith("Cancelled\n"));
        assertTrue(run("", "payments").out().contains("#1"));
        var r = run("y\n", "unpay", "7");
        assertEquals("Error: No payment #7\n", r.err());
        assertEquals("", r.out());
    }
    @Test void paidWithoutSuggestionFails() {
        assertEquals("Error: No suggested payment from Alex to Sam\n", run("", "paid", "Alex", "Sam").err());
    }
    @Test void allSettled() {
        run("", "paid", "Sam", "Alex"); run("", "paid", "Priya", "Alex");
        assertEquals("All settled up.\n", run("", "owes").out());
    }
}
