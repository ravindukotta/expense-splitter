package splitter.settlement;

import java.math.BigDecimal;

public record Transfer(String from, String to, BigDecimal amount) {
}
