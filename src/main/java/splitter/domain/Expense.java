package splitter.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;

public record Expense(int id, String description, BigDecimal amount, String paidBy, Split split, LocalDate date) {

    public LinkedHashMap<String, BigDecimal> shares(List<String> memberOrder) {
        return Shares.of(amount, split, memberOrder);
    }
}
