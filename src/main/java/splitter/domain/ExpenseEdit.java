package splitter.domain;

import java.math.BigDecimal;
import java.util.Optional;

/** Fields to change on an expense; empty fields are left as they are. */
public record ExpenseEdit(Optional<String> description, Optional<BigDecimal> amount,
                          Optional<String> paidBy, Optional<SplitSpec> split) {
}
