package splitter.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import splitter.domain.Ledger;
import splitter.domain.SplitterException;
import splitter.settlement.MonthlyReport;

@Command(name = "report", description = "Write a monthly HTML report.")
final class ReportCommand implements Callable<Integer> {
    private static final DateTimeFormatter MONTH_NAME = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    @ParentCommand
    private SplitCommand parent;

    @Option(names = "--month", description = "Month to report as YYYY-MM (default: current month)")
    private String monthText;

    @Option(names = "--out", description = "Output file (default: report-YYYY-MM.html)")
    private Path out;

    @Override
    public Integer call() {
        Context ctx = parent.context();
        YearMonth month = parseMonth(ctx);
        Ledger ledger = ctx.store().load();
        MonthlyReport report = MonthlyReport.of(ledger.expenses(), ledger.payments(), month,
                ledger.members().order());
        if (report.nothingToReport()) {
            ctx.out().print("Nothing to report for " + MONTH_NAME.format(month) + ".\n");
            return 0;
        }
        Path target = out != null ? out : Path.of("report-" + month + ".html");
        try {
            Files.writeString(target, ReportHtml.render(report), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SplitterException("Could not write report " + target + ": " + e.getMessage());
        }
        ctx.out().print("Wrote " + target + "\n");
        return 0;
    }

    private YearMonth parseMonth(Context ctx) {
        if (monthText == null) {
            return YearMonth.from(ctx.today());
        }
        try {
            return YearMonth.parse(monthText);
        } catch (DateTimeParseException e) {
            throw new SplitterException("Invalid month '" + monthText + "'; expected YYYY-MM");
        }
    }
}
