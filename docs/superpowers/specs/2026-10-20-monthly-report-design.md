# Monthly Report — Design

Date: 2026-10-20
Builds on: `docs/superpowers/specs/2026-10-19-expense-splitter-design.md` (the existing `split` CLI)

## Intent

At the end of each month someone in the flat runs `split` to produce a **report file**, **attaches it to an email** to the flatmates, and everyone uses it to **decide who pays whom**. The report must answer, at a glance: *what do I need to pay, and to whom?* and then *why* — who spent what this month.

It's also a learning project: the owner is using it to practise the superpowers workflow (design → plan → worktree → subagent-driven build), so it should be a clean, well-tested addition to the existing codebase that follows its patterns.

## Goals

- A new command that writes a **basic, self-contained HTML file** for one month, suitable as an email attachment.
- The report leads with **"To settle up"**: the fewest payments that clear everything (e.g. "Alex pays Sam 482.25").
- Then **"Who spent what"**: one row per person with **Paid**, **Share**, **Earlier**, **Balance**.
- **Carry-over**: anything still unsettled from before the month rolls into the month's settle-up (the owner's choice).

## Non-goals

- Sending email. The tool only writes the file; a person attaches it.
- Listing every expense (the owner chose the short layout "A" over the full layout "B").
- Other formats (PDF, CSV, plain text) or styling options.
- Reports for ranges other than one calendar month, or per-category breakdowns.
- Changing how `owes`, `paid`, or balances work today.

## Anti-goals (fails even if it "works" when…)

- The "To settle up" payments don't actually clear the balances shown, or the rows don't add up.
- The file needs internet, scripts, or images to display — it must open offline on any phone or laptop mail client.
- Re-running an old month's report later gives different numbers because of things that happened after that month.

## Decided by the owner

- The report is emailed as an **attachment**; the tool **writes a file**, it doesn't send email.
- Format: **basic HTML**.
- Layout: **version A** — payments first, then the per-person table; no list of individual expenses.
- **Carry-over**: unsettled amounts from earlier months roll into the month's settle-up.
- The settle-up section shows suggested payments like "Jo pays Alex 20.00" (same fewest-payments greedy method as `owes`).

## The report (approved layout, version A with carry-over)

```
Flat expenses — October 2026
4 expenses · total 1,341.00

TO SETTLE UP
  Alex pays Sam    482.25
  Priya pays Sam   319.50
  Priya pays Jo     53.75

WHO SPENT WHAT
            Paid     Share   Earlier    Balance
  Alex     36.00    518.25      0.00    −482.25
  Sam   1,200.00    398.25      0.00    +801.75
  Priya    45.00    398.25    −20.00    −373.25
  Jo       60.00     26.25    +20.00     +53.75

Balance = Earlier + Paid − Share. Positive means the flat owes you; negative means you owe.
```

(Example: Priya still owed Jo 20.00 from September.)

## Definitions

For report month M (from the 1st to the last day of M):

- **This month's expenses**: expenses whose `date` is in M.
- **Paid** (per person): sum of amounts of this month's expenses they paid for.
- **Share** (per person): sum of their shares of this month's expenses (existing `Shares` rounding rules).
- **Earlier** (per person) *(assistant's call — see note)*: the balance from **all expenses dated before M** plus **all payments dated on or before the last day of M** (same sign convention as existing balances: positive = owed money).
- **Balance** = Earlier + Paid − Share. This equals the person's overall balance counting every expense and payment dated on or before the end of M.
- **To settle up** = `Settlement.simplify` applied to the Balance column (same greedy method and tie-break order as `owes`).
- **Total** = sum of this month's expense amounts; **expense count** = number of this month's expenses.

*Note on Earlier:* payments made during the month are folded into Earlier so that the table always adds up and the settle-up payments clear exactly the Balance column. The column header stays "Earlier"; the footnote explains Balance.

*Note on stability:* everything dated after M is ignored, so re-running an old month's report gives the same numbers (unless someone later edits or deletes an old expense, which is expected to change history).

## Who appears in the table

Every member (active or removed) for whom any of Paid, Share, Earlier, or Balance is non-zero, in member join order. Others are left out.

## Command (assistant's calls)

```
split report [--month YYYY-MM] [--out <path>]
```

- `--month` defaults to the current month (from the same "today" the app already uses).
- `--out` defaults to `report-YYYY-MM.html` in the current working directory. An existing file is overwritten.
- On success it prints one line: `Wrote report-2026-10.html` (the path it wrote, as given/defaulted).
- `--month` that isn't a valid `YYYY-MM` → `Error: Invalid month '<text>'; expected YYYY-MM`, exit non-zero, no file written.
- **Nothing to report** (no expenses in M and every Balance is zero): no file is written; prints `Nothing to report for October 2026.` and exits 0.
- If there's carry-over but no expenses in M, the report is still written (expense count 0, total 0.00, Paid/Share all 0.00) so the outstanding payments get emailed.
- If everything balances to zero but there were expenses, "To settle up" says **"Everyone is settled up."**
- The report never changes the data file.

## HTML (assistant's calls)

- One self-contained file: inline `<style>` only, no scripts, no external fonts, images or links. UTF-8, with `<meta charset="utf-8">` and a mobile viewport meta tag.
- Title: `Flat expenses — <Month yyyy>` (English month name), subtitle `<n> expense(s) · total <amount>`.
- Amounts in the HTML use thousands separators (`1,341.00`); Earlier and Balance show an explicit sign (`+801.75`, `−482.25` using the minus sign U+2212; zero as `0.00`). Positive balances green, negative red.
- All names are HTML-escaped (member names can contain `&`, `<`, etc.).
- Visual style: as in the approved mockup — a simple centred column, the settle-up box first with a light green background, then the table; readable on a phone.

## Structure (follow existing code)

- A pure calculation in `splitter.settlement` (no I/O) that builds the report data from expenses, payments, member order and the month: rows (name, paid, share, earlier, balance), the transfers, total, expense count. Unit-tested.
- HTML rendering as a separate pure function (data → HTML string). Unit-tested for content and escaping.
- A thin `ReportCommand` in `splitter.cli`, registered in `SplitCommand`, that loads the ledger, builds and renders the report, writes the file, prints the confirmation. CLI-tested with the existing `CliTestSupport`.
- Writing the file is plain `Files.writeString` (UTF-8); the data file's atomic-write rules don't apply to the report.

## Left to the builder

- Class and method names, exact CSS, and exact HTML element structure (keep close to the mockup).
- Exact wording of error messages other than those given above.
