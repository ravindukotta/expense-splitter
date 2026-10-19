# Expense Splitter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A `split` command-line tool for one flat. Flatmates record shared expenses (split equally or by percentage among some or all members), and the tool suggests the fewest payments to settle up and records payments as paid.

**Architecture:** There are four layers in the `splitter` package. `splitter.domain` holds the members, expenses, payments, share calculation and the `Ledger` aggregate. `splitter.settlement` holds the balances and greedy debt simplification. Both are pure Java with no I/O, and that's where most of the tests go. `splitter.storage` saves the ledger as one JSON file via Jackson. `splitter.cli` contains thin picocli commands that load the ledger, make one change, save, and print.

**Tech Stack:** Java 25, Maven, JUnit 5 (Jupiter), picocli 4.7.6, Jackson 2.18.2 (databind + jsr310), maven-shade-plugin for a runnable jar.

**Spec:** `docs/superpowers/specs/2026-10-19-expense-splitter-design.md`. Read it before starting any task.

## Global Constraints

- Java 25 (`maven.compiler.release` = 25). Build with `mvn`. Tests use JUnit 5.
- All money is `BigDecimal` with scale 2. Never use `double`/`float` for money.
- Amounts: positive, at most 2 decimal places. Percentages: positive, at most 2 decimal places, summing to exactly 100.
- Rounding: round each share down to the cent, then hand out the leftover cents one at a time **in the order the members were added to the group**.
- Settling up: greedy. Repeatedly match the person who owes the most with the person owed the most, and transfer the smaller of the two amounts. Ties go to whoever was added to the group first.
- Names are matched case-insensitively and displayed as first entered. An unknown name is always an error. A new person is never created implicitly.
- Default data file `~/.splitter/data.json`. The global `--file <path>` option comes before the subcommand (`split --file x.json list`). Writes go to a temp file in the same directory, then get renamed over the original.
- Errors: print `Error: <message>` to stderr, exit with a non-zero code, and leave the data file unchanged.
- No interactive menu mode, no multiple groups, no currencies, no categories, no partial payments.
- Every commit message ends with the line `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Duplicate names inside one expense** (`--with Alex,alex` or `--split Alex=50,alex=50`). Reject it with an error naming the duplicate; never double-count. Tested in Task 5.
2. **Malformed amounts** (`12,50`, `1e3`, `.5`, `12.`, ` 12`, `-5`, `0`, `12.345`). Reject them all with a message that quotes the input. `new BigDecimal` accepts `1e3`, so validate with a regex. Tested in Task 1.
3. **Member names that would break the argument syntax** (containing `,` or `=`, blank, or the keyword `everyone`). Reject at `member add`. Tested in Task 2.
4. **A corrupt or unreadable data file.** Fail with an error naming the path and never overwrite the file. A missing file means an empty ledger, and the folder is created on first save. Tested in Task 7.
5. **Ids that don't exist** for `show`/`edit`/`delete`/`unpay`. Give a clear "No expense #N" / "No payment #N" error, exit non-zero, and leave the data untouched. Tested in Tasks 5, 6 and 9.

---

## File Structure

```
pom.xml
split                                   # shell wrapper: java -jar target/splitter.jar "$@"
src/main/java/splitter/
  domain/SplitterException.java        # the one user-facing error type
  domain/Money.java                    # parse/format amounts and percentages
  domain/Member.java                   # record(name, active)
  domain/Members.java                  # ordered member list, lookup, add/reactivate/deactivate
  domain/Split.java                    # sealed: EqualSplit | PercentSplit (resolved, canonical names)
  domain/SplitSpec.java                # what the user asked for: everyone | equal(names) | percent(map)
  domain/Shares.java                   # amount + split -> per-person shares with cent rounding
  domain/Expense.java                  # record
  domain/ExpenseEdit.java              # record of Optional fields for edit
  domain/Payment.java                  # record
  domain/Ledger.java                   # aggregate: all operations + validation
  domain/LedgerData.java               # plain snapshot record used by storage
  settlement/Balances.java             # expenses + payments -> balance per member
  settlement/Settlement.java           # balances -> List<Transfer> (greedy)
  settlement/Transfer.java             # record(from, to, amount)
  storage/JsonStore.java               # load/save Ledger via LedgerData
  cli/App.java                         # main + execute(...) used by tests
  cli/SplitCommand.java                # root @Command, --file, Context
  cli/Context.java                     # store, in, out, today
  cli/Args.java                        # parse --with / --split strings into SplitSpec
  cli/Format.java                      # shared output lines
  cli/MemberCommand.java, MembersCommand.java, AddCommand.java, ListCommand.java,
  cli/ShowCommand.java, EditCommand.java, DeleteCommand.java, OwesCommand.java,
  cli/PaidCommand.java, PaymentsCommand.java, UnpayCommand.java
src/test/java/splitter/... mirrors the above; cli/CliTestSupport.java runs commands in-process
```

---

### Task 1: Project skeleton and Money

**Files:**
- Create: `pom.xml`, `src/main/java/splitter/domain/SplitterException.java`, `src/main/java/splitter/domain/Money.java`
- Test: `src/test/java/splitter/domain/MoneyTest.java`

**Interfaces:**
- Produces:
  - `class SplitterException extends RuntimeException { SplitterException(String message) }`
  - `final class Money`:
    - `static BigDecimal parseAmount(String text)`: positive, ≤2dp, returns scale 2. Otherwise throws `SplitterException("Invalid amount '<text>': …")`.
    - `static BigDecimal parsePercent(String text)`: same rules, scale 2. Message `"Invalid percentage '<text>': …"`.
    - `static String format(BigDecimal amount)`: always 2 decimals, e.g. `"36.00"`.

- [ ] **Step 1: Create `pom.xml`**

  groupId `splitter`, artifactId `expense-splitter`, version `0.1.0`, `maven.compiler.release` 25, UTF-8. Dependencies:
  - `info.picocli:picocli:4.7.6`
  - `com.fasterxml.jackson.core:jackson-databind:2.18.2`
  - `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2`
  - `org.junit.jupiter:junit-jupiter:5.11.4` (test)

  Plugins:
  - `maven-surefire-plugin` 3.5.2
  - `maven-shade-plugin` 3.6.0, bound to `package`, `finalName` `splitter`, `ManifestResourceTransformer` with main class `splitter.cli.App`

  Task 9 relies on the shaded jar being `target/splitter.jar`.

- [ ] **Step 2: Write the failing test**

```java
class MoneyTest {
    @Test void parsesWholeAndDecimalAmountsToScaleTwo() {
        assertEquals(new BigDecimal("36.00"), Money.parseAmount("36"));
        assertEquals(new BigDecimal("12.50"), Money.parseAmount("12.5"));
        assertEquals(new BigDecimal("0.01"), Money.parseAmount("0.01"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-5", "12.345", "12,50", "1e3", ".5", "12.", " 12", "abc", ""})
    void rejectsMalformedAmountsQuotingTheInput(String text) {
        var ex = assertThrows(SplitterException.class, () -> Money.parseAmount(text));
        assertTrue(ex.getMessage().contains("'" + text + "'"));
    }
    @Test void parsesPercentages() {
        assertEquals(new BigDecimal("33.33"), Money.parsePercent("33.33"));
        assertThrows(SplitterException.class, () -> Money.parsePercent("0"));
        assertThrows(SplitterException.class, () -> Money.parsePercent("10.001"));
    }
    @Test void formatsWithTwoDecimals() {
        assertEquals("36.00", Money.format(new BigDecimal("36")));
        assertEquals("0.50", Money.format(new BigDecimal("0.5")));
    }
}
```

- [ ] **Step 3: Run it and confirm it fails**

  Run: `mvn -q test -Dtest=MoneyTest`
  Expected: compilation failure (`Money` not found).

- [ ] **Step 4: Implement `SplitterException` and `Money`**

  Validate against the regex `^\d+(\.\d{1,2})?$` before calling `new BigDecimal`, then `setScale(2)` and reject zero.

- [ ] **Step 5: Run it and confirm it passes**

  Run: `mvn -q test -Dtest=MoneyTest`
  Expected: BUILD SUCCESS, all tests passed.

- [ ] **Step 6: Commit**

```bash
git add pom.xml src
git commit -m "feat: project skeleton and Money parsing"
```

---

### Task 2: Members

**Files:**
- Create: `src/main/java/splitter/domain/Member.java`, `src/main/java/splitter/domain/Members.java`
- Test: `src/test/java/splitter/domain/MembersTest.java`

**Interfaces:**
- Consumes: `SplitterException`.
- Produces:
  - `record Member(String name, boolean active)`
  - `final class Members`:
    - `Members()`
    - `Members(List<Member> existing)`
    - `List<Member> all()`: join order, including removed members.
    - `List<String> order()`: every name in join order.
    - `List<String> activeNames()`
    - `Member add(String name)`: trims the name. If it matches a removed member case-insensitively, that member is reactivated in their original position.
    - `String resolve(String input)`: canonical name for any member, active or removed. Unknown → `SplitterException("Unknown member '<input>'")`.
    - `String resolveActive(String input)`: like `resolve`, but a removed member → `SplitterException("<Name> has been removed from the flat")`.
    - `void deactivate(String canonicalName)`

- [ ] **Step 1: Write the failing test**

```java
class MembersTest {
    @Test void keepsJoinOrderAndResolvesCaseInsensitively() {
        var m = new Members();
        m.add("Alex"); m.add("Sam"); m.add(" Priya ");
        assertEquals(List.of("Alex", "Sam", "Priya"), m.order());
        assertEquals("Priya", m.resolve("PRIYA"));
    }
    @Test void rejectsDuplicateActiveName() {
        var m = new Members(); m.add("Jo");
        assertThrows(SplitterException.class, () -> m.add("jo"));
    }
    @ParameterizedTest @ValueSource(strings = {"", "   ", "A,B", "A=B", "everyone", "Everyone"})
    void rejectsNamesThatBreakArgumentSyntax(String bad) {
        assertThrows(SplitterException.class, () -> new Members().add(bad));
    }
    @Test void unknownNameErrorNamesTheInput() {
        var ex = assertThrows(SplitterException.class, () -> new Members().resolve("Zed"));
        assertEquals("Unknown member 'Zed'", ex.getMessage());
    }
    @Test void removedMemberResolvesButIsNotActive() {
        var m = new Members(); m.add("Alex"); m.add("Jo");
        m.deactivate("Jo");
        assertEquals("Jo", m.resolve("jo"));
        assertEquals(List.of("Alex"), m.activeNames());
        assertEquals("Jo has been removed from the flat",
            assertThrows(SplitterException.class, () -> m.resolveActive("Jo")).getMessage());
    }
    @Test void reAddingRemovedMemberReactivatesInOriginalPosition() {
        var m = new Members(); m.add("Alex"); m.add("Jo"); m.add("Sam");
        m.deactivate("Jo");
        m.add("JO");
        assertEquals(List.of("Alex", "Jo", "Sam"), m.order());
        assertEquals(List.of("Alex", "Jo", "Sam"), m.activeNames());
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

  Run: `mvn -q test -Dtest=MembersTest`
  Expected: compilation failure.

- [ ] **Step 3: Implement `Member` and `Members`**

  Store the members in an `ArrayList<Member>`. Reactivating replaces the record at the same index with `active=true`. A re-added member keeps the name as originally entered.

- [ ] **Step 4: Run it and confirm it passes**

  Run: `mvn -q test -Dtest=MembersTest`
  Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src && git commit -m "feat: members with case-insensitive lookup and reactivation"
```

---

### Task 3: Splits, shares and the Expense record

**Files:**
- Create: `src/main/java/splitter/domain/Split.java`, `src/main/java/splitter/domain/Shares.java`, `src/main/java/splitter/domain/Expense.java`
- Test: `src/test/java/splitter/domain/SharesTest.java`

**Interfaces:**
- Produces:
  - `sealed interface Split permits Split.Equal, Split.Percent`:
    - `record Equal(List<String> people) implements Split`
    - `record Percent(LinkedHashMap<String, BigDecimal> percentages) implements Split`
    - Names are always canonical member names.
    - `List<String> people()` is declared on `Split`. `Equal` gets it from its record component, and `Percent` returns its key set in order.
  - `final class Shares`:
    - `static LinkedHashMap<String, BigDecimal> of(BigDecimal amount, Split split, List<String> memberOrder)`: the result is ordered by `memberOrder`, and the values add up exactly to `amount`.
  - `record Expense(int id, String description, BigDecimal amount, String paidBy, Split split, LocalDate date)`
    - Adds a convenience method `LinkedHashMap<String, BigDecimal> shares(List<String> memberOrder)` that delegates to `Shares.of`.

- [ ] **Step 1: Write the failing test**

```java
class SharesTest {
    static final List<String> ORDER = List.of("Alex", "Sam", "Priya", "Jo");

    @Test void equalSplitGivesLeftoverCentsInJoinOrder() {
        var s = Shares.of(new BigDecimal("100.00"), new Split.Equal(List.of("Priya", "Sam", "Alex")), ORDER);
        assertEquals(List.of("Alex", "Sam", "Priya"), List.copyOf(s.keySet()));
        assertEquals(new BigDecimal("33.34"), s.get("Alex"));
        assertEquals(new BigDecimal("33.33"), s.get("Sam"));
        assertEquals(new BigDecimal("33.33"), s.get("Priya"));
    }
    @Test void percentSplitOfRent() {
        var p = new LinkedHashMap<String, BigDecimal>();
        p.put("Alex", new BigDecimal("40")); p.put("Sam", new BigDecimal("30")); p.put("Priya", new BigDecimal("30"));
        var s = Shares.of(new BigDecimal("1200.00"), new Split.Percent(p), ORDER);
        assertEquals(new BigDecimal("480.00"), s.get("Alex"));
        assertEquals(new BigDecimal("360.00"), s.get("Sam"));
        assertEquals(new BigDecimal("360.00"), s.get("Priya"));
    }
    @Test void percentSplitRoundsDownThenHandsOutLeftoverInJoinOrder() {
        var p = new LinkedHashMap<String, BigDecimal>();
        p.put("Jo", new BigDecimal("33.34")); p.put("Sam", new BigDecimal("33.33")); p.put("Alex", new BigDecimal("33.33"));
        var s = Shares.of(new BigDecimal("10.00"), new Split.Percent(p), ORDER);
        assertEquals(new BigDecimal("3.34"), s.get("Alex"));   // 3.333 -> 3.33 + leftover cent (first in join order)
        assertEquals(new BigDecimal("3.33"), s.get("Sam"));
        assertEquals(new BigDecimal("3.33"), s.get("Jo"));     // 3.334 -> 3.33
    }
    @Test void sharesAlwaysSumToAmount() {
        var s = Shares.of(new BigDecimal("0.05"), new Split.Equal(List.of("Alex", "Sam", "Priya", "Jo")), ORDER);
        assertEquals(new BigDecimal("0.05"), s.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(new BigDecimal("0.02"), s.get("Alex"));
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

  Run: `mvn -q test -Dtest=SharesTest`
  Expected: compilation failure.

- [ ] **Step 3: Implement `Split`, `Shares` and `Expense`**

  Each person's raw share is `amount / n` (Equal) or `amount × pct / 100` (Percent). Use `setScale(2, RoundingMode.DOWN)` on each. Take `leftover = amount − sum`, in cents, and add 0.01 to each participant in `memberOrder` order, wrapping around if needed, until the leftover is used up.

- [ ] **Step 4: Run it and confirm it passes**

  Run: `mvn -q test -Dtest=SharesTest`
  Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src && git commit -m "feat: equal and percentage shares with exact cent rounding"
```

---

### Task 4: Balances and settlement

**Files:**
- Create: `src/main/java/splitter/domain/Payment.java`, `src/main/java/splitter/settlement/Balances.java`, `src/main/java/splitter/settlement/Settlement.java`, `src/main/java/splitter/settlement/Transfer.java`
- Test: `src/test/java/splitter/settlement/BalancesTest.java`, `src/test/java/splitter/settlement/SettlementTest.java`

**Interfaces:**
- Consumes: `Expense`, `Expense.shares(List<String>)`.
- Produces:
  - `record Payment(int id, String from, String to, BigDecimal amount, LocalDate date)` in `splitter.domain`
  - `record Transfer(String from, String to, BigDecimal amount)`
  - `final class Balances`:
    - `static LinkedHashMap<String, BigDecimal> compute(List<Expense> expenses, List<Payment> payments, List<String> memberOrder)`
    - Every name in `memberOrder` is present (zero included), in that order.
    - Positive = is owed money.
    - The formula is the spec's: paid − shares + payments made − payments received.
    - Every value has scale 2, so a zero comes out as `0.00`.
  - `final class Settlement`:
    - `static List<Transfer> simplify(LinkedHashMap<String, BigDecimal> balances)`
    - The map's iteration order is the tie-break order.

- [ ] **Step 1: Write the failing tests**

```java
class BalancesTest {
    static final List<String> ORDER = List.of("Alex", "Sam", "Priya", "Jo");
    static final LocalDate D = LocalDate.of(2026, 10, 19);

    @Test void payerIsCreditedAndSharersDebited() {
        var e = new Expense(1, "Movie tickets", new BigDecimal("36.00"), "Alex",
            new Split.Equal(List.of("Alex", "Sam", "Priya")), D);
        var b = Balances.compute(List.of(e), List.of(), ORDER);
        assertEquals(List.of("Alex", "Sam", "Priya", "Jo"), List.copyOf(b.keySet()));
        assertEquals(new BigDecimal("24.00"), b.get("Alex"));
        assertEquals(new BigDecimal("-12.00"), b.get("Sam"));
        assertEquals(new BigDecimal("0.00"), b.get("Jo"));
    }
    @Test void paymentMovesBalanceTowardZero() {
        var e = new Expense(1, "x", new BigDecimal("20.00"), "Alex", new Split.Equal(List.of("Jo")), D);
        var p = new Payment(1, "Jo", "Alex", new BigDecimal("20.00"), D);
        var b = Balances.compute(List.of(e), List.of(p), ORDER);
        assertEquals(0, b.get("Alex").signum());
        assertEquals(0, b.get("Jo").signum());
    }
}

class SettlementTest {
    static LinkedHashMap<String, BigDecimal> balances(Object... nameAmount) { /* builds an ordered map from pairs */ }

    @Test void chainCollapsesToOnePayment() {          // Jo owes Sam 20, Sam owes Alex 20
        var t = Settlement.simplify(balances("Alex", "20", "Sam", "0", "Jo", "-20"));
        assertEquals(List.of(new Transfer("Jo", "Alex", new BigDecimal("20.00"))), t);
    }
    @Test void greedyLargestDebtorToLargestCreditor() {
        var t = Settlement.simplify(balances("Alex", "50", "Sam", "10", "Priya", "-40", "Jo", "-20"));
        assertEquals(List.of(
            new Transfer("Priya", "Alex", new BigDecimal("40.00")),
            new Transfer("Jo", "Alex", new BigDecimal("10.00")),
            new Transfer("Jo", "Sam", new BigDecimal("10.00"))), t);
    }
    @Test void tiesBrokenByJoinOrder() {
        var t = Settlement.simplify(balances("Alex", "30", "Sam", "30", "Priya", "-30", "Jo", "-30"));
        assertEquals(List.of(
            new Transfer("Priya", "Alex", new BigDecimal("30.00")),
            new Transfer("Jo", "Sam", new BigDecimal("30.00"))), t);
    }
    @Test void allZeroMeansNoTransfers() {
        assertEquals(List.of(), Settlement.simplify(balances("Alex", "0", "Sam", "0")));
    }
}
```

  Write the `balances` helper body for real. It converts each amount with `new BigDecimal(s).setScale(2)`.

- [ ] **Step 2: Run them and confirm they fail**

  Run: `mvn -q test -Dtest='BalancesTest,SettlementTest'`
  Expected: compilation failure.

- [ ] **Step 3: Implement `Payment`, `Transfer`, `Balances` and `Settlement`**

  `simplify` works on a mutable copy. While any balance is non-zero:
  - The debtor is the member with the most negative balance. On a tie, take the first in map order.
  - The creditor is the member with the most positive balance. On a tie, take the first in map order.
  - Transfer `min(−debtor, creditor)` from the debtor to the creditor, and update both balances.

  Compare amounts with `compareTo`/`signum`, never `equals`, because the scales may differ.

- [ ] **Step 4: Run them and confirm they pass**

  Run: `mvn -q test -Dtest='BalancesTest,SettlementTest'`
  Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src && git commit -m "feat: balances and greedy settle-up suggestions"
```

---

### Task 5: Ledger — expenses

**Files:**
- Create: `src/main/java/splitter/domain/SplitSpec.java`, `src/main/java/splitter/domain/ExpenseEdit.java`, `src/main/java/splitter/domain/Ledger.java`
- Test: `src/test/java/splitter/domain/LedgerExpenseTest.java`

**Interfaces:**
- Consumes: `Members`, `Split`, `Shares`, `Expense`, `Money`.
- Produces:
  - `sealed interface SplitSpec`:
    - `record Everyone()`
    - `record Equal(List<String> names)`
    - `record Percent(LinkedHashMap<String, BigDecimal> percentages)`
    - Names here are raw user input.
  - `record ExpenseEdit(Optional<String> description, Optional<BigDecimal> amount, Optional<String> paidBy, Optional<SplitSpec> split)`
  - `final class Ledger`:
    - `Ledger()`
    - `Members members()`
    - `List<Expense> expenses()`: unmodifiable, in id order.
    - `Expense expense(int id)`: throws `SplitterException("No expense #<id>")`.
    - `Member addMember(String name)`: delegates to `Members.add`.
    - `Expense addExpense(String description, BigDecimal amount, String paidBy, SplitSpec spec, LocalDate date)`
    - `Expense editExpense(int id, ExpenseEdit edit)`
    - `Expense deleteExpense(int id)`: returns the removed expense.

  Rules enforced inside `Ledger` (see the spec's "Behaviour rules"):
  - The description must not be blank. The amount must be > 0.
  - The payer and every person on the split must resolve with `resolveActive`. On **edit**, a name already on the expense (as payer or participant) only needs `resolve`, so old expenses with removed members stay editable.
  - `Everyone` resolves to the `activeNames()` at the time, and the expense **stores that explicit list**, so people who join later aren't added to old expenses. If there are no active members, it's an error.
  - Duplicate people after resolving are an error: `"<Name> is listed twice"`. An empty `Equal` list is an error.
  - Percentages: each one must be > 0, and they must sum to exactly 100. Otherwise the error is `"Percentages add up to <sum>, not 100"`, with the sum formatted to 2 decimals. Stored percentages are normalised to scale 2, and stored amounts too.
  - Ids start at 1, go up by 1, and are **never reused** after a delete. Keep a `nextExpenseId` counter.
  - Edit: only the fields given change. A new `SplitSpec` replaces the old split completely. So `--with` on a percentage expense becomes an equal split, while changing only the amount keeps the percentages.

- [ ] **Step 1: Write the failing test**

```java
class LedgerExpenseTest {
    static final LocalDate D = LocalDate.of(2026, 10, 19);
    Ledger ledger;
    @BeforeEach void flat() {
        ledger = new Ledger();
        for (var n : List.of("Alex", "Sam", "Priya", "Jo")) ledger.addMember(n);
    }
    static LinkedHashMap<String, BigDecimal> pct(String... nameValue) { /* ordered map from pairs */ }

    @Test void addsEqualSplitAmongNamedPeopleWithCanonicalNames() {
        var e = ledger.addExpense("Movie tickets", new BigDecimal("36.00"), "alex",
            new SplitSpec.Equal(List.of("alex", "SAM", "Priya")), D);
        assertEquals(1, e.id());
        assertEquals("Alex", e.paidBy());
        assertEquals(new Split.Equal(List.of("Alex", "Sam", "Priya")), e.split());
    }
    @Test void everyoneIsFrozenAtTimeOfAdding() {
        var e = ledger.addExpense("Broadband Sept", new BigDecimal("45.00"), "Priya", new SplitSpec.Everyone(), D);
        ledger.addMember("Kim");
        assertEquals(List.of("Alex", "Sam", "Priya", "Jo"), ledger.expense(e.id()).split().people());
    }
    @Test void percentSplit() {
        var e = ledger.addExpense("Rent Oct", new BigDecimal("1200.00"), "Sam",
            new SplitSpec.Percent(pct("Alex", "40", "Sam", "30", "Priya", "30")), D);
        assertEquals(new BigDecimal("480.00"), e.shares(ledger.members().order()).get("Alex"));
    }
    @Test void percentagesMustSumTo100() {
        var ex = assertThrows(SplitterException.class, () -> ledger.addExpense("Rent", new BigDecimal("100.00"), "Sam",
            new SplitSpec.Percent(pct("Alex", "40", "Sam", "30", "Priya", "29")), D));
        assertEquals("Percentages add up to 99.00, not 100", ex.getMessage());
    }
    @Test void rejectsDuplicatePeopleIgnoringCase() {
        var ex = assertThrows(SplitterException.class, () -> ledger.addExpense("x", new BigDecimal("10.00"), "Alex",
            new SplitSpec.Equal(List.of("Alex", "alex")), D));
        assertEquals("Alex is listed twice", ex.getMessage());
        assertThrows(SplitterException.class, () -> ledger.addExpense("x", new BigDecimal("10.00"), "Alex",
            new SplitSpec.Percent(pct("Sam", "50", "sam", "50")), D));
    }
    @Test void rejectsUnknownPayerAndBlankDescription() {
        assertThrows(SplitterException.class, () -> ledger.addExpense("x", new BigDecimal("1.00"), "Zed", new SplitSpec.Everyone(), D));
        assertThrows(SplitterException.class, () -> ledger.addExpense(" ", new BigDecimal("1.00"), "Alex", new SplitSpec.Everyone(), D));
    }
    @Test void payerNeedNotBeInvolved() {
        var e = ledger.addExpense("Gift", new BigDecimal("10.00"), "Alex", new SplitSpec.Equal(List.of("Sam")), D);
        assertEquals(List.of("Sam"), e.split().people());
    }
    @Test void idsAreNeverReused() {
        ledger.addExpense("a", new BigDecimal("1.00"), "Alex", new SplitSpec.Everyone(), D);
        var b = ledger.addExpense("b", new BigDecimal("1.00"), "Alex", new SplitSpec.Everyone(), D);
        ledger.deleteExpense(b.id());
        assertEquals(3, ledger.addExpense("c", new BigDecimal("1.00"), "Alex", new SplitSpec.Everyone(), D).id());
    }
    @Test void unknownIdErrors() {
        assertEquals("No expense #99", assertThrows(SplitterException.class, () -> ledger.deleteExpense(99)).getMessage());
        assertThrows(SplitterException.class, () -> ledger.editExpense(99, new ExpenseEdit(Optional.of("x"), Optional.empty(), Optional.empty(), Optional.empty())));
    }
    @Test void editAmountKeepsPercentages() {
        var e = ledger.addExpense("Rent", new BigDecimal("1000.00"), "Sam",
            new SplitSpec.Percent(pct("Alex", "40", "Sam", "60")), D);
        var u = ledger.editExpense(e.id(), new ExpenseEdit(Optional.empty(), Optional.of(new BigDecimal("2000.00")), Optional.empty(), Optional.empty()));
        assertInstanceOf(Split.Percent.class, u.split());
        assertEquals(new BigDecimal("800.00"), u.shares(ledger.members().order()).get("Alex"));
        assertEquals("Rent", u.description());
    }
    @Test void editWithOnPercentExpenseBecomesEqual() {
        var e = ledger.addExpense("Rent", new BigDecimal("1000.00"), "Sam",
            new SplitSpec.Percent(pct("Alex", "40", "Sam", "60")), D);
        var u = ledger.editExpense(e.id(), new ExpenseEdit(Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(new SplitSpec.Equal(List.of("Alex", "Sam")))));
        assertEquals(new Split.Equal(List.of("Alex", "Sam")), u.split());
    }
}
```

  Tests involving removed members are in Task 6, because removing a member needs balances.

- [ ] **Step 2: Run it and confirm it fails**

  Run: `mvn -q test -Dtest=LedgerExpenseTest`
  Expected: compilation failure.

- [ ] **Step 3: Implement `SplitSpec`, `ExpenseEdit` and the expense parts of `Ledger`**

  Validate everything before changing any state, so a rejected edit leaves the expense exactly as it was.

- [ ] **Step 4: Run it and confirm it passes**

  Run: `mvn -q test -Dtest=LedgerExpenseTest`
  Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src && git commit -m "feat: ledger expense add/edit/delete with validation"
```

---

### Task 6: Ledger — members leaving, settling up, payments

**Files:**
- Create: `src/main/java/splitter/domain/LedgerData.java`
- Modify: `src/main/java/splitter/domain/Ledger.java`
- Test: `src/test/java/splitter/domain/LedgerSettlementTest.java`

**Interfaces:**
- Consumes: `Balances.compute`, `Settlement.simplify`, `Payment`, `Transfer`.
- Produces (new `Ledger` methods):
  - `void removeMember(String name)`: resolves the name with `resolveActive`. A non-zero balance → `SplitterException("<Name> still has a balance of <signed amount>; settle up first")`.
  - `List<Payment> payments()`: in id order.
  - `LinkedHashMap<String, BigDecimal> balances()`
  - `List<Transfer> suggestedPayments()`
  - `Payment recordPayment(String from, String to, LocalDate date)`: both names go through `resolve`, so removed members are allowed. The amount is the matching suggested transfer. If there's no match → `SplitterException("No suggested payment from <From> to <To>")`. Ids start at 1 and are never reused.
  - `Payment unpay(int id)`: returns the removed payment. Unknown id → `SplitterException("No payment #<id>")`.
  - `LedgerData snapshot()` and `static Ledger restore(LedgerData data)`
  - `record LedgerData(List<Member> members, List<Expense> expenses, List<Payment> payments, int nextExpenseId, int nextPaymentId)`

- [ ] **Step 1: Write the failing test**

```java
class LedgerSettlementTest {
    // same @BeforeEach flat (Alex, Sam, Priya, Jo) and date D as LedgerExpenseTest

    @Test void chainSuggestsOnePaymentAndPayingClearsIt() {
        ledger.addExpense("a", new BigDecimal("20.00"), "Sam", new SplitSpec.Equal(List.of("Jo")), D);
        ledger.addExpense("b", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Sam")), D);
        assertEquals(List.of(new Transfer("Jo", "Alex", new BigDecimal("20.00"))), ledger.suggestedPayments());
        var p = ledger.recordPayment("jo", "ALEX", D);
        assertEquals(new Payment(1, "Jo", "Alex", new BigDecimal("20.00"), D), p);
        assertEquals(List.of(), ledger.suggestedPayments());
    }
    @Test void payingWrongDirectionOrNonexistentIsAnError() {
        ledger.addExpense("a", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Jo")), D);
        assertEquals("No suggested payment from Alex to Jo",
            assertThrows(SplitterException.class, () -> ledger.recordPayment("Alex", "Jo", D)).getMessage());
        assertThrows(SplitterException.class, () -> ledger.recordPayment("Sam", "Alex", D));
    }
    @Test void unpayRestoresTheSuggestion() {
        ledger.addExpense("a", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Jo")), D);
        var p = ledger.recordPayment("Jo", "Alex", D);
        ledger.unpay(p.id());
        assertEquals(1, ledger.suggestedPayments().size());
        assertEquals("No payment #7", assertThrows(SplitterException.class, () -> ledger.unpay(7)).getMessage());
    }
    @Test void editingAfterPaymentSuggestsRefund() {
        var e = ledger.addExpense("a", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Jo")), D);
        ledger.recordPayment("Jo", "Alex", D);
        ledger.editExpense(e.id(), new ExpenseEdit(Optional.empty(), Optional.of(new BigDecimal("15.00")), Optional.empty(), Optional.empty()));
        assertEquals(List.of(new Transfer("Alex", "Jo", new BigDecimal("5.00"))), ledger.suggestedPayments());
        assertEquals(1, ledger.payments().size());
    }
    @Test void cannotRemoveMemberWithBalance() {
        ledger.addExpense("a", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Jo")), D);
        var ex = assertThrows(SplitterException.class, () -> ledger.removeMember("Jo"));
        assertEquals("Jo still has a balance of -20.00; settle up first", ex.getMessage());
    }
    @Test void removedMemberLeftOutOfEveryoneAndNewExpenses() {
        ledger.removeMember("Jo");
        var e = ledger.addExpense("x", new BigDecimal("3.00"), "Alex", new SplitSpec.Everyone(), D);
        assertEquals(List.of("Alex", "Sam", "Priya"), e.split().people());
        assertThrows(SplitterException.class, () -> ledger.addExpense("y", new BigDecimal("1.00"), "Jo", new SplitSpec.Everyone(), D));
    }
    @Test void oldExpenseWithRemovedMemberStaysEditableAndTheyCanSettle() {
        var e = ledger.addExpense("a", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Jo")), D);
        ledger.recordPayment("Jo", "Alex", D);
        ledger.removeMember("Jo");
        ledger.editExpense(e.id(), new ExpenseEdit(Optional.empty(), Optional.of(new BigDecimal("30.00")), Optional.empty(), Optional.empty()));
        assertEquals(List.of(new Transfer("Jo", "Alex", new BigDecimal("10.00"))), ledger.suggestedPayments());
        ledger.recordPayment("Jo", "Alex", D);
        assertEquals(List.of(), ledger.suggestedPayments());
    }
    @Test void editCannotAddRemovedMemberAsNewParticipant() {
        ledger.removeMember("Jo");
        var e = ledger.addExpense("a", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Sam")), D);
        assertThrows(SplitterException.class, () -> ledger.editExpense(e.id(), new ExpenseEdit(Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(new SplitSpec.Equal(List.of("Sam", "Jo"))))));
    }
    @Test void snapshotRestoreRoundTripKeepsCounters() {
        var e = ledger.addExpense("a", new BigDecimal("20.00"), "Alex", new SplitSpec.Equal(List.of("Jo")), D);
        ledger.deleteExpense(e.id());
        var copy = Ledger.restore(ledger.snapshot());
        assertEquals(2, copy.addExpense("b", new BigDecimal("1.00"), "Alex", new SplitSpec.Everyone(), D).id());
        assertEquals(ledger.members().all(), copy.members().all());
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

  Run: `mvn -q test -Dtest=LedgerSettlementTest`
  Expected: compilation failure.

- [ ] **Step 3: Implement the new `Ledger` methods and `LedgerData`**

  The signed amount in the remove error uses `Money.format` on the raw balance, which can be negative. Members who were removed but still have a balance are already handled, because `Balances` uses `members().order()`, which includes them.

- [ ] **Step 4: Run all domain tests and confirm they pass**

  Run: `mvn -q test`
  Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src && git commit -m "feat: member removal, settle-up suggestions and payments"
```

---

### Task 7: JSON storage

**Files:**
- Create: `src/main/java/splitter/storage/JsonStore.java`
- Test: `src/test/java/splitter/storage/JsonStoreTest.java`

**Interfaces:**
- Consumes: `Ledger.snapshot()`, `Ledger.restore(LedgerData)`, `Split`.
- Produces: `final class JsonStore`:
  - `JsonStore(Path file)`
  - `Ledger load()`: a missing file gives `new Ledger()`. An unreadable or invalid file → `SplitterException("Could not read data file <path>: <reason>")`.
  - `void save(Ledger ledger)`: creates the parent folders, writes `<file>.tmp` in the same folder, then does `Files.move` with `ATOMIC_MOVE` and `REPLACE_EXISTING`.
  - `static Path defaultPath()`: `~/.splitter/data.json`, based on `System.getProperty("user.home")`.

- [ ] **Step 1: Write the failing test** (use JUnit `@TempDir Path dir`)

```java
class JsonStoreTest {
    @TempDir Path dir;

    @Test void missingFileLoadsEmptyLedger() {
        assertEquals(List.of(), new JsonStore(dir.resolve("none.json")).load().expenses());
    }
    @Test void roundTripsEverything() {
        var ledger = new Ledger();
        for (var n : List.of("Alex", "Sam", "Jo")) ledger.addMember(n);
        var pct = new LinkedHashMap<String, BigDecimal>(); pct.put("Alex", new BigDecimal("40")); pct.put("Sam", new BigDecimal("60"));
        ledger.addExpense("Rent", new BigDecimal("1000.00"), "Sam", new SplitSpec.Percent(pct), LocalDate.of(2026, 10, 1));
        ledger.addExpense("Tickets", new BigDecimal("30.00"), "Alex", new SplitSpec.Equal(List.of("Alex", "Jo")), LocalDate.of(2026, 10, 2));
        ledger.recordPayment("Jo", "Alex", LocalDate.of(2026, 10, 3));
        var store = new JsonStore(dir.resolve("sub/data.json"));
        store.save(ledger);
        var loaded = store.load();
        assertEquals(ledger.snapshot(), loaded.snapshot());
        assertEquals(ledger.suggestedPayments(), loaded.suggestedPayments());
        assertFalse(Files.exists(dir.resolve("sub/data.json.tmp")));
    }
    @Test void corruptFileFailsAndIsLeftUntouched() throws IOException {
        var file = dir.resolve("data.json");
        Files.writeString(file, "{not json");
        var ex = assertThrows(SplitterException.class, () -> new JsonStore(file).load());
        assertTrue(ex.getMessage().startsWith("Could not read data file " + file));
        assertEquals("{not json", Files.readString(file));
    }
}
```

  `roundTripsEverything` relies on `LedgerData` record equality, which compares `BigDecimal` with `equals`, so scale matters. That works because the `Ledger` stores amounts and percentages at scale 2 (Task 5) and `WRITE_BIGDECIMAL_AS_PLAIN` keeps the trailing zeros.

- [ ] **Step 2: Run it and confirm it fails**

  Run: `mvn -q test -Dtest=JsonStoreTest`
  Expected: compilation failure.

- [ ] **Step 3: Implement `JsonStore`**

  Set up the `ObjectMapper` like this:
  - Register `JavaTimeModule` and disable `WRITE_DATES_AS_TIMESTAMPS`, so dates are written as ISO strings.
  - Enable `INDENT_OUTPUT`.
  - Enable `JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN`.
  - Register a **mix-in** for `Split`: `@JsonTypeInfo(use = NAME, property = "type")` plus `@JsonSubTypes` naming `equal` → `Split.Equal` and `percent` → `Split.Percent`. The mix-in keeps Jackson annotations out of the domain package.

  Read and write `LedgerData`.

- [ ] **Step 4: Run it and confirm it passes**

  Run: `mvn -q test -Dtest=JsonStoreTest`
  Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src && git commit -m "feat: JSON storage with atomic writes"
```

---

### Task 8: CLI — members, add, list, show

**Files:**
- Create: `src/main/java/splitter/cli/App.java`, `SplitCommand.java`, `Context.java`, `Args.java`, `Format.java`, `MemberCommand.java`, `MembersCommand.java`, `AddCommand.java`, `ListCommand.java`, `ShowCommand.java`
- Test: `src/test/java/splitter/cli/CliTestSupport.java`, `ArgsTest.java`, `MemberAndAddCommandTest.java`

**Interfaces:**
- Consumes: `Ledger`, `JsonStore`, `Money`, `SplitSpec`.
- Produces:
  - `final class App`:
    - `public static void main(String[] args)`: calls `System.exit(execute(args, System.in, System.out, System.err, LocalDate.now()))`.
    - `static int execute(String[] args, InputStream in, PrintStream out, PrintStream err, LocalDate today)`: builds the `CommandLine`. Its execution-exception handler prints `Error: <message>` to `err` and returns 1 for a `SplitterException`.
  - `record Context(JsonStore store, BufferedReader in, PrintStream out, LocalDate today)`
  - `SplitCommand` is the root `@Command(name = "split", subcommands = {…})` with `@Option(names = "--file")`. When `--file` is absent it falls back to `JsonStore.defaultPath()`. Subcommands get the `Context` through `@ParentCommand`.
  - `final class Args`:
    - `static SplitSpec with(String text)`: `everyone` (case-insensitive) → `Everyone`. Otherwise split on `,`, trim each name, and drop blanks. An empty result → `SplitterException("--with needs at least one name")`.
    - `static SplitSpec.Percent split(String text)`: `Name=pct` pairs separated by `,`, with each percentage parsed by `Money.parsePercent`. A malformed pair → `SplitterException("Invalid --split entry '<pair>'; expected Name=percent")`.
  - `final class Format`:
    - `static String expenseHeadline(String verb, Expense e)` gives `"<verb> #14 Movie tickets 36.00 paid by Alex"`.
    - `static String sharesLine(Expense e, List<String> order)` gives `"  Alex 12.00 · Sam 12.00 · Priya 12.00"`.
  - The commands, with exact output where the spec shows it:
    - `member add <name>` prints `Added member <Name>`.
    - `member remove <name>` prints `Removed member <Name>`.
    - `members` prints one active name per line. With none, it prints `No members yet.`
    - `add <description> <amount> --paid-by <name> [--with …] [--split …]` prints the headline with verb `Added`, then the shares line. Passing both `--with` and `--split` → error `Use either --with or --split, not both`. Without either, it uses `Everyone`.
    - `list` prints ` #<id>  <date>  <description>  <amount>  <payer>`, one line per expense in id order, with columns aligned. With none, it prints `No expenses yet.`
    - `show <id>` prints the headline with verb `Expense`, then `  Date: <date>`, `  Split: equally` or `  Split: by percentage (Alex 40.00%, …)`, then the shares line.

  Every command that changes data does load → change → save → print, all inside `call()`. If an exception is thrown before saving, nothing gets written.

- [ ] **Step 1: Write the test support and the failing tests**

  `CliTestSupport` holds a `@TempDir` data file and has a method `Result run(String stdin, String... args)`. It runs `App.execute(["--file", file, ...args], …, LocalDate.of(2026, 10, 19))` and captures the output. `record Result(int exitCode, String out, String err)`.

```java
class ArgsTest {
    @Test void everyoneKeyword() { assertEquals(new SplitSpec.Everyone(), Args.with("Everyone")); }
    @Test void namesTrimmed() { assertEquals(new SplitSpec.Equal(List.of("Alex", "Sam")), Args.with(" Alex , Sam ")); }
    @Test void emptyWithRejected() { assertThrows(SplitterException.class, () -> Args.with(" , ")); }
    @Test void splitParsesPairsInOrder() {
        assertEquals(List.of("Alex", "Sam"), List.copyOf(Args.split("Alex=40,Sam=60").percentages().keySet()));
    }
    @ParameterizedTest @ValueSource(strings = {"Alex", "Alex=", "=40", "Alex=forty"})
    void malformedSplitRejected(String s) { assertThrows(SplitterException.class, () -> Args.split(s)); }
}

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
}
```

  Write output with `\n` line endings (`out.print(... + "\n")`, not `println`) so the tests behave the same on every platform.

- [ ] **Step 2: Run them and confirm they fail**

  Run: `mvn -q test -Dtest='ArgsTest,MemberAndAddCommandTest'`
  Expected: compilation failure.

- [ ] **Step 3: Implement `App`, `SplitCommand`, `Context`, `Args`, `Format` and the five commands**

- [ ] **Step 4: Run them and confirm they pass**

  Run: `mvn -q test`
  Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src && git commit -m "feat: CLI for members, add, list and show"
```

---

### Task 9: CLI — edit, delete, owes, paid, payments, unpay, and the `split` script

**Files:**
- Create: `src/main/java/splitter/cli/EditCommand.java`, `DeleteCommand.java`, `OwesCommand.java`, `PaidCommand.java`, `PaymentsCommand.java`, `UnpayCommand.java`, `split` (repo root, executable)
- Modify: `src/main/java/splitter/cli/SplitCommand.java` (register the subcommands)
- Test: `src/test/java/splitter/cli/EditAndSettleCommandTest.java`

**Interfaces:**
- Consumes: everything from Task 8, plus `Ledger.editExpense/deleteExpense/suggestedPayments/recordPayment/payments/unpay`.
- Produces these commands and outputs:
  - `edit <id> [--description] [--amount] [--paid-by] [--with] [--split]` prints the headline with verb `Updated`, then the shares line.
    - `--with` together with `--split` → the same error as `add`.
    - No options at all → `Error: Nothing to change`.
  - `delete <id>` prompts `Delete #<id> <description> <amount>? [y/N]: ` on `out` and reads one line from `in`.
    - `y`/`yes` (case-insensitive) → prints `Deleted #<id>`.
    - Anything else → prints `Cancelled` and saves nothing.
  - `owes` prints one line per transfer: `  <From> pays <To>  <amount>`, with the names padded to align. With no transfers, it prints `All settled up.`
  - `paid <from> <to>` prints `Recorded payment #<id>: <From> paid <To> <amount>`.
  - `payments` prints ` #<id>  <date>  <From> paid <To> <amount>`. With none, it prints `No payments yet.`
  - `unpay <id>` prompts `Undo payment #<id>: <From> paid <To> <amount>? [y/N]: `.
    - Yes → prints `Removed payment #<id>`.
    - Otherwise → prints `Cancelled`.
  - `split` script: `#!/bin/sh` followed by `exec java -jar "$(dirname "$0")/target/splitter.jar" "$@"`. Mark it executable with `chmod +x`.

- [ ] **Step 1: Write the failing test**

```java
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
    @Test void deleteAsksAndRespectsAnswer() {
        assertTrue(run("n\n", "delete", "1").out().endsWith("Cancelled\n"));
        var r = run("y\n", "delete", "1");
        assertEquals("Delete #1 Movie tickets 36.00? [y/N]: Deleted #1\n", r.out());
        assertEquals("No expenses yet.\n", run("", "list").out());
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
        run("y\n", "unpay", "1");
        assertEquals("No payments yet.\n", run("", "payments").out());
    }
    @Test void paidWithoutSuggestionFails() {
        assertEquals("Error: No suggested payment from Alex to Sam\n", run("", "paid", "Alex", "Sam").err());
    }
    @Test void allSettled() {
        run("", "paid", "Sam", "Alex"); run("", "paid", "Priya", "Alex");
        assertEquals("All settled up.\n", run("", "owes").out());
    }
}
```

- [ ] **Step 2: Run it and confirm it fails**

  Run: `mvn -q test -Dtest=EditAndSettleCommandTest`
  Expected: compilation failure.

- [ ] **Step 3: Implement the six commands and the `split` script, and register them in `SplitCommand`**

  `delete` and `unpay` look up the item first: an unknown id → error, with no prompt. They then prompt, and save only on yes.

- [ ] **Step 4: Run the full suite and confirm it passes**

  Run: `mvn -q test`
  Expected: PASS.

- [ ] **Step 5: Build the jar and smoke-test the script end to end**

  Run:
```bash
mvn -q package -DskipTests && F=$(mktemp -d)/d.json && \
./split --file "$F" member add Alex && ./split --file "$F" member add Jo && \
./split --file "$F" add "Movie tickets" 20 --paid-by Alex --with Jo && \
./split --file "$F" owes && ./split --file "$F" paid Jo Alex && ./split --file "$F" owes
```

  Expected output, in order:
  - `Added member Alex`
  - `Added member Jo`
  - `Added #1 Movie tickets 20.00 paid by Alex`
  - `  Jo 20.00`
  - a `Jo pays Alex 20.00` line
  - `Recorded payment #1: Jo paid Alex 20.00`
  - `All settled up.`

- [ ] **Step 6: Commit**

```bash
git add split src && git commit -m "feat: CLI edit, delete, settle-up, payments and split script"
```
