package splitter.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Payment(int id, String from, String to, BigDecimal amount, LocalDate date) {
}
