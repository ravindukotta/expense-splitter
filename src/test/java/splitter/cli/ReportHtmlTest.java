package splitter.cli;

import org.junit.jupiter.api.Test;
import splitter.settlement.MonthlyReport;
import splitter.settlement.Transfer;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportHtmlTest {

    static MonthlyReport.Row row(String name, String paid, String share, String earlier, String balance) {
        return new MonthlyReport.Row(name, new BigDecimal(paid), new BigDecimal(share),
            new BigDecimal(earlier), new BigDecimal(balance));
    }

    static final MonthlyReport OCT_REPORT = new MonthlyReport(YearMonth.of(2026, 10), 4, new BigDecimal("1341.00"),
        List.of(row("Alex", "36.00", "518.25", "0.00", "-482.25"), row("Sam", "1200.00", "398.25", "0.00", "801.75")),
        List.of(new Transfer("Alex", "Sam", new BigDecimal("482.25"))));

    @Test void formats_amounts() {
        assertEquals("1,341.00", ReportHtml.amount(new BigDecimal("1341.00")));
        assertEquals("-482.25", ReportHtml.amount(new BigDecimal("-482.25")));
        assertEquals("+801.75", ReportHtml.signed(new BigDecimal("801.75")));
        assertEquals("−482.25", ReportHtml.signed(new BigDecimal("-482.25")));
        assertEquals("0.00", ReportHtml.signed(new BigDecimal("0.00")));
    }

    @Test void renders_title_subtitle_payments_and_rows() {
        String html = ReportHtml.render(OCT_REPORT);
        assertTrue(html.startsWith("<!DOCTYPE html>"));
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
}
