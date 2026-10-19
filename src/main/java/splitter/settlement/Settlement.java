package splitter.settlement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Settlement {
    private Settlement() {
    }

    /** Greedy: largest debtor pays largest creditor; ties go to the earliest in map order. */
    public static List<Transfer> simplify(LinkedHashMap<String, BigDecimal> balances) {
        var remaining = new LinkedHashMap<>(balances);
        var transfers = new ArrayList<Transfer>();
        while (true) {
            String debtor = extreme(remaining, -1);
            String creditor = extreme(remaining, 1);
            if (debtor == null || creditor == null) {
                return transfers;
            }
            BigDecimal amount = remaining.get(debtor).negate().min(remaining.get(creditor));
            transfers.add(new Transfer(debtor, creditor, amount.setScale(2)));
            remaining.merge(debtor, amount, BigDecimal::add);
            remaining.merge(creditor, amount, BigDecimal::subtract);
        }
    }

    /** First entry with the largest magnitude among those with the given sign, or null if none. */
    private static String extreme(Map<String, BigDecimal> balances, int sign) {
        String best = null;
        BigDecimal bestValue = null;
        for (var entry : balances.entrySet()) {
            BigDecimal value = entry.getValue();
            if (value.signum() != sign) {
                continue;
            }
            if (bestValue == null || value.abs().compareTo(bestValue.abs()) > 0) {
                best = entry.getKey();
                bestValue = value;
            }
        }
        return best;
    }
}
