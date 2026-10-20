# Monthly Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `split report [--month YYYY-MM] [--out <path>]`. It writes a self-contained HTML file for one month that leads with "To settle up" payments (including carry-over), then a per-person Paid / Share / Earlier / Balance table.

**Architecture:** Three layers, following the existing code:
- A pure calculation `splitter.settlement.MonthlyReport` builds the report data from expenses, payments, member order and month, reusing `Balances` and `Settlement`.
- A pure renderer `splitter.cli.ReportHtml` turns that data into an HTML string.
- A thin picocli `ReportCommand` loads the ledger, renders the report, writes the file and prints a confirmation.

**Tech Stack:** Java 25, Maven, JUnit 5, picocli 4.7.6. There are no new dependencies.

**Spec:** `docs/superpowers/specs/2026-10-20-monthly-report-design.md`. The original app spec is `docs/superpowers/specs/2026-10-19-expense-splitter-design.md`.

## Global Constraints

- No new dependencies. All money is `BigDecimal` at scale 2. Never use `double`/`float`.
- **Earlier** = balance from all expenses dated **before** the month, plus all payments dated **on or before the month's last day**. Positive means the person is owed money.
- **Balance** = Earlier + Paid − Share. **To settle up** = `Settlement.simplify` over the Balance column, in member join order.
- Anything dated after the month is ignored. The report never changes the data file.
- Rows are every member in `members().order()` (active or removed) with any non-zero Paid, Share, Earlier or Balance. Everyone else is left out.
- HTML must be self-contained: inline `<style>` only, with no `<script>`, no external fonts, images or links. It uses UTF-8 with `<meta charset="utf-8">` and a mobile viewport meta tag, and every name is HTML-escaped.
- HTML amounts use thousands separators (`1,341.00`). Earlier and Balance are signed: `+801.75`, `−482.25` with U+2212 as the minus, and zero shown as `0.00`.
- Output text and line endings follow the existing CLI (`\n`, UTF-8). Errors print `Error: <message>` to stderr and exit non-zero.
- Every commit message ends with the `Co-Authored-By:` line for the model that wrote the commit.

## Review Focus

1. **A member name containing HTML characters** (`Tom & Jerry`, `<b>`) must be escaped, never injected. Tested in Task 2.
2. **`--out` pointing into a folder that doesn't exist** must give `Error: Could not write report <path>: …`, exit 1, and no stack trace. Tested in Task 3.
3. **An expense or payment dated after the month** must not change that month's numbers. Tested in Task 1.
4. **Running the report** must leave the data file byte-for-byte unchanged. Tested in Task 3.
5. **A month with carry-over but no expenses** must still produce a report with `0 expenses · total 0.00` and the payments. Tested in Tasks 1 and 3.

---

### Task 1: MonthlyReport calculation

**Files:**
- Create: `src/main/java/splitter/settlement/MonthlyReport.java`
- Test: `src/test/java/splitter/settlement/MonthlyReportTest.java`

**Interfaces:**
- Consumes these existing types:
  - `Balances.compute(List<Expense>, List<Payment>, List<String> memberOrder)`: an ordered map, scale 2.
  - `Settlement.simplify(LinkedHashMap<String, BigDecimal>)`: returns `List<Transfer>`.
  - `Expense.shares(List<String>)` and `Expense.date()`.
  - `Payment.date()`.
- Produces:
  - `public record MonthlyReport(YearMonth month, int expenseCount, BigDecimal total, List<MonthlyReport.Row> rows, List<Transfer> settleUp)`
  - `public record Row(String name, BigDecimal paid, BigDecimal share, BigDecimal earlier, BigDecimal balance)`, nested in `MonthlyReport`. All values are scale 2.
  - `public static MonthlyReport of(List<Expense> expenses, List<Payment> payments, YearMonth month, List<String> memberOrder)`
  - `public boolean nothingToReport()`: true when `expenseCount == 0` and `settleUp` is empty.

- [ ] **Step 1: Write the failing test**

  Use these fixtures:
  - `ORDER = List.of("Alex", "Sam", "Priya", "Jo")` and `OCT = YearMonth.of(2026, 10)`.
  - October expenses, the spec's example:
    - Rent Oct 1200.00, paid by Sam on 2026-10-01, `Percent` split Alex 40.00, Sam 30.00, Priya 30.00.
    - Broadband Oct 45.00, paid by Priya on 2026-10-03, `Equal` split across all four.
    - Movie tickets 36.00, paid by Alex on 2026-10-11, `Equal` split across Alex, Sam and Priya.
    - Groceries 60.00, paid by Jo on 2026-10-18, `Equal` split across all four.
  - `SEPT_DEBT`: 20.00, paid by Jo on 2026-09-15, `Equal` split with Priya only.

```java
@Test void spec_example_with_carry_over() {
    var r = MonthlyReport.of(withSeptDebt(octoberExpenses()), List.of(), OCT, ORDER);
    assertEquals(4, r.expenseCount());
    assertEquals(new BigDecimal("1341.00"), r.total());
    assertEquals(List.of(
        row("Alex", "36.00", "518.25", "0.00", "-482.25"),
        row("Sam", "1200.00", "398.25", "0.00", "801.75"),
        row("Priya", "45.00", "398.25", "-20.00", "-373.25"),
        row("Jo", "60.00", "26.25", "20.00", "53.75")), r.rows());
    assertEquals(List.of(
        new Transfer("Alex", "Sam", new BigDecimal("482.25")),
        new Transfer("Priya", "Sam", new BigDecimal("319.50")),
        new Transfer("Priya", "Jo", new BigDecimal("53.75"))), r.settleUp());
}
@Test void payment_during_month_is_folded_into_earlier() {
    var pay = new Payment(1, "Priya", "Jo", new BigDecimal("20.00"), LocalDate.of(2026, 10, 5));
    var r = MonthlyReport.of(withSeptDebt(octoberExpenses()), List.of(pay), OCT, ORDER);
    assertEquals(row("Priya", "45.00", "398.25", "0.00", "-353.25"), r.rows().get(2));
    assertEquals(new Transfer("Priya", "Jo", new BigDecimal("33.75")), r.settleUp().get(2));
}
@Test void anything_after_the_month_is_ignored() {
    var later = new ArrayList<>(octoberExpenses());
    later.add(expense(9, "500.00", "Alex", new Split.Equal(ORDER), LocalDate.of(2026, 11, 1)));
    var pay = new Payment(1, "Alex", "Sam", new BigDecimal("482.25"), LocalDate.of(2026, 11, 2));
    assertEquals(MonthlyReport.of(octoberExpenses(), List.of(), OCT, ORDER),
                 MonthlyReport.of(later, List.of(pay), OCT, ORDER));
}
@Test void carry_over_without_expenses_still_reports() {
    var r = MonthlyReport.of(octoberExpenses(), List.of(), YearMonth.of(2026, 11), ORDER);
    assertEquals(0, r.expenseCount());
    assertEquals(new BigDecimal("0.00"), r.total());
    assertEquals(row("Alex", "0.00", "0.00", "-482.25", "-482.25"), r.rows().get(0));
    assertFalse(r.nothingToReport());
}
@Test void members_with_all_zero_are_left_out() {
    var r = MonthlyReport.of(List.of(expense(1, "10.00", "Alex", new Split.Equal(List.of("Sam")),
        LocalDate.of(2026, 10, 2))), List.of(), OCT, ORDER);
    assertEquals(List.of("Alex", "Sam"), r.rows().stream().map(MonthlyReport.Row::name).toList());
}
@Test void empty_month_with_nothing_owed_is_nothing_to_report() {
    var r = MonthlyReport.of(List.of(), List.of(), OCT, ORDER);
    assertTrue(r.nothingToReport());
    assertEquals(List.of(), r.rows());
}
```

  The helpers `row(name, paid, share, earlier, balance)`, `expense(...)`, `octoberExpenses()` and `withSeptDebt(...)` are test-only and build values with `new BigDecimal(text)`. Keep percentages at scale 2 (`"40.00"`). Expected values are written at scale 2, because record equality compares `BigDecimal` scale.

- [ ] **Step 2: Run it and confirm it fails**

  Run: `mvn -q test -Dtest=MonthlyReportTest`
  Expected: compilation failure (`MonthlyReport` not found).

- [ ] **Step 3: Implement `MonthlyReport.of`**

  1. Compute `earlier` with `Balances.compute(expenses dated before month.atDay(1), payments dated on or before month.atEndOfMonth(), memberOrder)`.
  2. Sum Paid and Share over the expenses dated in the month.
  3. Set `balance = earlier + paid − share` for each name in `memberOrder`.
  4. Set `settleUp = Settlement.simplify(balances)`.
  5. Keep the rows that have any non-zero value.

  Store `List.copyOf` lists.

- [ ] **Step 4: Run it and confirm it passes**

  Run: `mvn -q test -Dtest=MonthlyReportTest`, then `mvn -q test`
  Expected: PASS, and the full suite is green.

- [ ] **Step 5: Commit**

```bash
git add src && git commit -m "feat: monthly report calculation with carry-over"
```

---

### Task 2: HTML rendering

**Files:**
- Create: `src/main/java/splitter/cli/ReportHtml.java`
- Test: `src/test/java/splitter/cli/ReportHtmlTest.java`

**Interfaces:**
- Consumes: `MonthlyReport`, `MonthlyReport.Row`, `Transfer` (from Task 1).
- Produces a `final class ReportHtml` (package-private) with:
  - `static String render(MonthlyReport report)`
  - `static String amount(BigDecimal value)`: `1341` gives `"1,341.00"`, and `-482.25` gives `"-482.25"` with a plain hyphen.
  - `static String signed(BigDecimal value)`: `801.75` gives `"+801.75"`, `-482.25` gives `"−482.25"` (U+2212), and `0` gives `"0.00"`.

  The page starts with `<!DOCTYPE html>` (Task 3 relies on this). Each transfer line puts `Alex pays Sam` in one plain text run, with no tags between the words, because Task 3 greps for it. The test's `row(...)` helper is the same one Task 1's test uses: build the values with `new BigDecimal(text)`.

  The content the page must contain is the spec's approved layout:
  - The `<title>` and the `<h1>` are both `Flat expenses — October 2026`, using an English month name and an em dash.
  - The subtitle is `4 expenses · total 1,341.00`. With one expense it reads `1 expense`.
  - The section heading is `To settle up`, with one line per transfer: `Alex pays Sam` followed by the amount via `amount(...)`. When `settleUp` is empty, it shows `Everyone is settled up.`
  - The section heading is `Who spent what`. The table header is Paid / Share / Earlier / Balance. Each row shows the name, `amount(paid)`, `amount(share)`, `signed(earlier)` and `signed(balance)`. A positive balance gets class `pos` (green), and a negative one gets class `neg` (red).
  - The footnote is `Balance = Earlier + Paid − Share. Positive means the flat owes you; negative means you owe.`
  - The styling is inline CSS only, as described in the spec's HTML section.

- [ ] **Step 1: Write the failing test**

```java
static final MonthlyReport OCT_REPORT = new MonthlyReport(YearMonth.of(2026, 10), 4, new BigDecimal("1341.00"),
    List.of(row("Alex", "36.00", "518.25", "0.00", "-482.25"), row("Sam", "1200.00", "398.25", "0.00", "801.75")),
    List.of(new Transfer("Alex", "Sam", new BigDecimal("482.25"))));

@Test void formats_amounts() {
    assertEquals("1,341.00", ReportHtml.amount(new BigDecimal("1341.00")));
    assertEquals("+801.75", ReportHtml.signed(new BigDecimal("801.75")));
    assertEquals("−482.25", ReportHtml.signed(new BigDecimal("-482.25")));
    assertEquals("0.00", ReportHtml.signed(new BigDecimal("0.00")));
}
@Test void renders_title_subtitle_payments_and_rows() {
    String html = ReportHtml.render(OCT_REPORT);
    assertTrue(html.contains("<title>Flat expenses — October 2026</title>"));
    assertTrue(html.contains("4 expenses · total 1,341.00"));
    assertTrue(html.contains("Alex pays Sam"));
    assertTrue(html.contains("482.25"));
    assertTrue(html.contains("1,200.00"));
    assertTrue(html.contains("+801.75"));
    assertTrue(html.contains("−482.25"));
    assertTrue(html.contains("Balance = Earlier + Paid − Share."));
}
@Test void is_self_contained() {
    String html = ReportHtml.render(OCT_REPORT).toLowerCase();
    assertTrue(html.contains("<meta charset=\"utf-8\">"));
    assertTrue(html.contains("name=\"viewport\""));
    assertFalse(html.contains("<script"));
    assertFalse(html.contains("http://"));
    assertFalse(html.contains("https://"));
    assertFalse(html.contains("<img"));
}
@Test void single_expense_and_settled_wording() {
    var r = new MonthlyReport(YearMonth.of(2026, 10), 1, new BigDecimal("45.00"),
        List.of(row("Priya", "45.00", "45.00", "0.00", "0.00")), List.of());
    String html = ReportHtml.render(r);
    assertTrue(html.contains("1 expense · total 45.00"));
    assertTrue(html.contains("Everyone is settled up."));
}
@Test void escapes_names() {
    var r = new MonthlyReport(YearMonth.of(2026, 10), 1, new BigDecimal("10.00"),
        List.of(row("Tom & <b>Jerry</b>", "10.00", "0.00", "0.00", "10.00")), List.of());
    String html = ReportHtml.render(r);
    assertTrue(html.contains("Tom &amp; &lt;b&gt;Jerry&lt;/b&gt;"));
    assertFalse(html.contains("<b>Jerry"));
}
```

- [ ] **Step 2: Run it and confirm it fails**

  Run: `mvn -q test -Dtest=ReportHtmlTest`
  Expected: compilation failure.

- [ ] **Step 3: Implement `ReportHtml`**

  Use a text block for the page skeleton and `StringBuilder` for the rows. Format amounts with `DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH))`. Escape names by replacing `&`, `<`, `>`, `"` and `'`.

- [ ] **Step 4: Run it and confirm it passes**

  Run: `mvn -q test -Dtest=ReportHtmlTest`, then `mvn -q test`
  Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src && git commit -m "feat: render monthly report as self-contained HTML"
```

---

### Task 3: `split report` command

**Files:**
- Create: `src/main/java/splitter/cli/ReportCommand.java`
- Modify: `src/main/java/splitter/cli/SplitCommand.java` (add `ReportCommand.class` to `subcommands`)
- Test: `src/test/java/splitter/cli/ReportCommandTest.java` (extends the existing `CliTestSupport`, whose "today" is 2026-10-19 and whose data file is `dir.resolve("data.json")`)

**Interfaces:**
- Consumes:
  - `MonthlyReport.of(...)` and `nothingToReport()` (Task 1)
  - `ReportHtml.render(...)` (Task 2)
  - `Context.store().load()`, `Context.today()`, `Context.out()`
  - `Ledger.expenses()`, `payments()`, `members().order()`
- Produces `@Command(name = "report")` with:
  - `--month` (a String, parsed with `YearMonth.parse`, defaulting to `YearMonth.from(ctx.today())`)
  - `--out` (a `Path`, defaulting to `Path.of("report-" + month + ".html")`, e.g. `report-2026-10.html`)

  Its behaviour:
  - An invalid month throws `SplitterException("Invalid month '<text>'; expected YYYY-MM")`.
  - If `nothingToReport()`, it prints `Nothing to report for <Month yyyy>.` (for example `Nothing to report for September 2026.`), writes no file and exits 0.
  - Otherwise it writes the file with `Files.writeString(out, html, UTF_8)`, overwriting any existing file, and prints `Wrote <out>`, where `<out>` is the path as given or defaulted.
  - An `IOException` becomes `SplitterException("Could not write report " + out + ": " + e.getMessage())`.

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run it and confirm it fails**

  Run: `mvn -q test -Dtest=ReportCommandTest`
  Expected: compilation failure (`ReportCommand` missing), or failures because `report` isn't a known subcommand.

- [ ] **Step 3: Implement `ReportCommand` and register it in `SplitCommand`**

  Follow the existing command pattern (`@ParentCommand SplitCommand parent; parent.context()`, as in `OwesCommand`). Never call `store().save(...)`.

- [ ] **Step 4: Run the full suite and confirm it passes**

  Run: `mvn -q test`
  Expected: all tests pass, the existing 88 plus the new ones.

- [ ] **Step 5: Build the jar and smoke-test end to end**

  Run these from the repo root, using any scratch folder for `$D`:
```bash
mvn -q package -DskipTests
./split --file "$D/d.json" member add Alex && ./split --file "$D/d.json" member add Jo
./split --file "$D/d.json" add "Movie tickets" 20 --paid-by Alex --with Jo
./split --file "$D/d.json" report --out "$D/r.html" && grep -c "Jo pays Alex" "$D/r.html"
```

  Expected output:
  - `Wrote <D>/r.html`
  - then `1`

- [ ] **Step 6: Commit**

```bash
git add src && git commit -m "feat: split report command writes monthly HTML report"
```
