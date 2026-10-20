package splitter.cli;

import splitter.settlement.MonthlyReport;
import splitter.settlement.Transfer;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/** Renders a {@link MonthlyReport} as one self-contained HTML page. */
final class ReportHtml {

    private static final String STYLE = """
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
               color: #222; margin: 0; padding: 24px 16px; background: #fff; }
        main { max-width: 560px; margin: 0 auto; }
        h1 { font-size: 22px; margin: 0 0 4px; }
        .sub { color: #666; margin: 0 0 20px; }
        h2 { font-size: 16px; margin: 0 0 8px; }
        .settle { background: #f3f8f2; border: 1px solid #cfe3cb; border-radius: 8px;
                  padding: 12px 16px; margin-bottom: 24px; }
        .settle ul { list-style: none; margin: 0; padding: 0; }
        .settle li { padding: 4px 0; }
        .settle .amt { font-weight: 600; font-variant-numeric: tabular-nums; }
        table { width: 100%; border-collapse: collapse; }
        th, td { padding: 8px 6px; text-align: right; font-variant-numeric: tabular-nums;
                 border-bottom: 1px solid #eee; }
        th:first-child, td:first-child { text-align: left; }
        th { font-size: 13px; color: #666; font-weight: 600; }
        .pos { color: #2e7d32; }
        .neg { color: #b03a2e; }
        .note { color: #888; font-size: 12px; margin-top: 12px; }
        """;

    private ReportHtml() {
    }

    static String render(MonthlyReport report) {
        String month = monthName(report.month());
        String title = "Flat expenses — " + month;
        String count = report.expenseCount() + (report.expenseCount() == 1 ? " expense" : " expenses");

        var settle = new StringBuilder();
        if (report.settleUp().isEmpty()) {
            settle.append("<p>Everyone is settled up.</p>\n");
        } else {
            settle.append("<ul>\n");
            for (Transfer t : report.settleUp()) {
                settle.append("<li>").append(escape(t.from())).append(" pays ").append(escape(t.to()))
                    .append(" <span class=\"amt\">").append(amount(t.amount())).append("</span></li>\n");
            }
            settle.append("</ul>\n");
        }

        var rows = new StringBuilder();
        for (MonthlyReport.Row r : report.rows()) {
            String cls = r.balance().signum() > 0 ? " class=\"pos\"" : r.balance().signum() < 0 ? " class=\"neg\"" : "";
            rows.append("<tr><td>").append(escape(r.name())).append("</td><td>").append(amount(r.paid()))
                .append("</td><td>").append(amount(r.share())).append("</td><td>").append(signed(r.earlier()))
                .append("</td><td").append(cls).append(">").append(signed(r.balance())).append("</td></tr>\n");
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>%s</title>
            <style>
            %s</style>
            </head>
            <body>
            <main>
            <h1>%s</h1>
            <p class="sub">%s · total %s</p>
            <section class="settle">
            <h2>To settle up</h2>
            %s</section>
            <h2>Who spent what</h2>
            <table>
            <tr><th>Name</th><th>Paid</th><th>Share</th><th>Earlier</th><th>Balance</th></tr>
            %s</table>
            <p class="note">Balance = Earlier + Paid − Share. Positive means the flat owes you; negative means you owe. Earlier = carried over from before this month, plus payments made up to the end of it.</p>
            </main>
            </body>
            </html>
            """.formatted(title, STYLE, title, count, amount(report.total()), settle, rows);
    }

    /** English month and year, e.g. "October 2026". */
    static String monthName(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
    }

    /** Thousands separators and two decimals; a negative value keeps a plain hyphen. */
    static String amount(BigDecimal value) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH)).format(value);
    }

    /** Explicit sign: "+801.75", "−482.25" (U+2212), and "0.00" for zero. */
    static String signed(BigDecimal value) {
        return switch (value.signum()) {
            case 1 -> "+" + amount(value);
            case -1 -> "−" + amount(value.negate());
            default -> amount(value);
        };
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
