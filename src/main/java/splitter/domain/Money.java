package splitter.domain;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Parsing and formatting of money amounts and percentages (always scale 2). */
public final class Money {
    private static final Pattern POSITIVE_TWO_DP = Pattern.compile("^\\d+(\\.\\d{1,2})?$");

    private Money() {
    }

    public static BigDecimal parseAmount(String text) {
        return parse(text, "amount");
    }

    public static BigDecimal parsePercent(String text) {
        return parse(text, "percentage");
    }

    public static String format(BigDecimal amount) {
        return amount.setScale(2).toPlainString();
    }

    private static BigDecimal parse(String text, String kind) {
        String prefix = "Invalid " + kind + " '" + text + "': ";
        if (text == null || !POSITIVE_TWO_DP.matcher(text).matches()) {
            throw new SplitterException(prefix + "expected a positive number with at most 2 decimal places");
        }
        BigDecimal value = new BigDecimal(text).setScale(2);
        if (value.signum() == 0) {
            throw new SplitterException(prefix + "must be greater than zero");
        }
        return value;
    }
}
