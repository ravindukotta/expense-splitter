# Expense Splitter — Design

Date: 2026-10-19

## Intent

A command-line tool for one shared flat. Flatmates record shared expenses
(rent, broadband, movie tickets, groceries) on a single shared computer, and
the tool tells them who should pay whom to settle up, using as few payments
as possible.

Why it exists: it's a **learning project**. The owner's main goal is to
learn the superpowers workflow (design → plan → test-driven build) on a
real but modest app. So the app should be small enough to finish, built
cleanly, and well tested. It doesn't need to be feature-rich.

The real-world pain it's based on: in a shared flat, expenses come in many
kinds, often only some of the flatmates are involved, and sometimes the split
is unequal (by percentage, for example rent split by room size).

## Goals

- Record an expense: description, amount, who paid, who was involved, how it's split.
- Two ways to split: **equally** among the people involved (the default), or **by percentage**.
- Not everyone has to be in every expense. People who aren't involved owe nothing for it.
- Edit and delete expenses.
- Show **who owes whom** as a short list of suggested payments that clears all debts, using as few payments as possible (for example, "Jo pays Alex" instead of Jo → Sam → Alex).
- Mark a suggested payment as **paid**. Payments are always the full suggested amount.
- Undo a payment that was marked by mistake.
- Keep all data between runs.

## Non-goals

- Multiple groups. There's exactly one group, the flat.
- Multiple currencies or conversion. Amounts are plain numbers.
- Categories, budgets or spending reports. The description is just a label.
- Partial payments.
- Syncing between computers, user accounts or logins. There's one shared machine.
- Any graphical or web interface. It's terminal only.
- An interactive menu mode. The owner chose one-shot commands (see below).

## Anti-goals (it fails even if it "works" when…)

- Shares don't add up exactly to the expense amount, or balances drift by a cent over time.
- Editing or deleting an old expense corrupts or silently loses payment history.
- A typo in a name quietly creates a new person or puts money on the wrong person.
- The code is a tangle that's hard to learn from or test. Clarity matters more than cleverness here.

## Decided by the owner

- **One group only**: the flat's members.
- **Each expense has one payer** (if two people pay at the till, enter two expenses).
- **Split equally by default.** Percentages are used only when the split is unequal.
- **Debt simplification**: suggest the fewest payments, not each underlying debt pair by pair.
- **Payments are always for the full amount**, and can be marked as paid.
- **Editing and deleting expenses** is required.
- **Editing old expenses after payments**: the payments stay as they are, balances are recalculated, and any new imbalance shows up as a new suggested payment (possibly someone being owed money back).
- **Interaction style: one command per action** (style "B" in the mockups), not an interactive menu.
- **One shared computer**: data lives in one local file.
- **Language: Java.**

## Command surface

Example session (the owner approved this look; the amounts in `owes` are only illustrative):

```
$ split add "Movie tickets" 36 --paid-by Alex --with Alex,Sam,Priya
Added #14 Movie tickets 36.00 paid by Alex
  Alex 12.00 · Sam 12.00 · Priya 12.00

$ split add "Rent Oct" 1200 --paid-by Sam --split Alex=40,Sam=30,Priya=30
Added #15 Rent Oct 1200.00 paid by Sam
  Alex 480.00 · Sam 360.00 · Priya 360.00

$ split list
 #12  Groceries        60.00  Alex
 #13  Broadband Sept   45.00  Priya
 #14  Movie tickets    36.00  Alex

$ split edit 14 --amount 48 --with everyone
Updated #14 Movie tickets 48.00 paid by Alex
  Alex 12.00 · Sam 12.00 · Priya 12.00 · Jo 12.00

$ split delete 16
Delete #16 Movie tickets 36.00? [y/N]: y
Deleted #16

$ split owes
  Jo    pays Alex  20.00
  Priya pays Sam  372.00

$ split paid Jo Alex
Recorded payment #3: Jo paid Alex 20.00
```

Commands:

| Command | Does |
|---|---|
| `split member add <name>` | Add a flatmate. |
| `split member remove <name>` | Remove a flatmate (see rules). |
| `split members` | List current flatmates. |
| `split add <description> <amount> --paid-by <name> [--with <names>\|everyone] [--split Name=pct,…]` | Add an expense. |
| `split list` | List expenses with id, date, description, amount, payer. |
| `split show <id>` | Show one expense including each person's share. |
| `split edit <id> [--description …] [--amount …] [--paid-by …] [--with …] [--split …]` | Change only the named fields. |
| `split delete <id>` | Delete after a `[y/N]` confirmation. |
| `split owes` | Show the suggested settle-up payments. |
| `split paid <from> <to>` | Record that the current suggested payment from → to was paid in full. |
| `split payments` | List recorded payments with ids. |
| `split unpay <id>` | Undo a recorded payment (with confirmation). |

A global `--file <path>` option points the app at a different data file.

## Behaviour rules

**Members**
- Names are matched case-insensitively but displayed as first entered. Duplicate names are rejected.
- Any unknown name in any command is an error with a clear message. A new person is never created implicitly.
- Removing a member is refused while their balance isn't zero. A removed member stays in the history (old expenses and payments still show their name) but isn't included in "everyone" for new expenses, and can't be named in new expenses.
- *(Assistant's call)* Old expenses that include a removed member can still be edited or deleted. The "can't be named" rule only applies to names newly added by the edit; people already on the expense can stay. If an edit or delete leaves a removed member with a non-zero balance, they still show up in `owes` and can be named in `paid` until they're settled, so the totals always balance.
- *(Assistant's call)* Adding a removed member's name again brings back the same person: their history is kept, and so is their original place in the member order. It doesn't create a second person with the same name.

**Expenses**
- The amount must be positive with at most 2 decimal places.
- Leaving out `--with`, or passing `--with everyone`, means all current members.
- The payer doesn't have to be among the people involved (for example, buying something for others).
- `--split` lists the involved people and their percentages. It replaces `--with` (passing both is an error). Percentages must be positive, have at most 2 decimal places, and add up to exactly 100.
- Each expense gets an increasing id that's never reused, and records the date it was entered.
- Editing: only the fields given change. If `--with` is changed on an expense that was split by percentage, it goes back to an equal split among the new people. To change to a different percentage split, use `--split` on its own: it sets both who's involved and the percentages. *(Assistant's call)* Passing `--with` and `--split` together is an error on `edit`, the same as on `add`. Every change is checked against the same rules as `add`, apart from the removed-member exception above.

**Money and rounding**
- All money uses `BigDecimal` with 2 decimal places and no floating point.
- Shares are computed by rounding each share down to the cent. The leftover cents are then handed out one at a time, in the order the members were added to the group. So shares always add up exactly to the amount, and the result is predictable and testable.

**Balances and settling**
- Each person's balance = what they paid for expenses − their shares of expenses + payments they made − payments they received.
- `split owes` turns the balances into payments using a greedy approach: repeatedly match the person who owes the most with the person owed the most, and transfer the smaller of the two amounts. Ties are broken by the order members were added, so the output is deterministic. *(Greedy doesn't guarantee the mathematical minimum in every case. It's the standard approach and clearly good enough here.)*
- `split paid <from> <to>` records a payment for the amount `owes` currently suggests between those two people. If there's no such suggestion, it's an error. The payment stores the amount and the date.
- Payments are separate records. Editing or deleting expenses never changes them.

**Output and errors**
- Every command that changes data prints a one-line confirmation of what changed (plus the shares, for expenses).
- Errors go to stderr with a non-zero exit code and never leave the data file half-written.

## Storage

- One JSON file, by default at `~/.splitter/data.json`, created on first use. `--file` overrides the location.
- It contains members (with the order they were added and whether they're active), expenses (each with its people and either an equal split or percentages, so shares can be recalculated), payments, and the next-id counters.
- Write safely: write to a temporary file in the same directory, then rename it over the original.

## Technical choices (assistant's calls, not discussed in depth)

- **Java 25** (installed), **Maven**, **JUnit 5**.
- **picocli** for parsing commands and arguments.
- **Jackson** for the JSON file.
- A small `split` shell script that runs the built jar, so commands read as above.
- Package layout in clear layers: a **domain** layer (members, expenses, split rules, rounding) and a **settlement** layer (balances, greedy simplification), both pure and without I/O. Then a **storage** layer (JSON repository) and a **CLI** layer (picocli commands, which are thin). Most tests target the domain and settlement layers. The CLI gets a few end-to-end tests using a temporary data file.
- Build test-first (superpowers TDD), since learning the workflow is the point.

## Left to the builder

- Exact column widths and formatting of `list`, `owes` and `payments` (keep them close to the examples).
- Exact wording of error messages (clear, and naming the bad input).
- Internal class names and the JSON field layout.
- Whether `split show` and `split members` print extra detail beyond the basics.
