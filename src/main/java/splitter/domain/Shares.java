package splitter.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;

/** Computes each participant's share of an expense, to the exact cent. */
public final class Shares {
    private static final BigDecimal CENT = new BigDecimal("0.01");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private Shares() {
    }

    /**
     * Rounds each share down to the cent, then hands the leftover cents out one at a time to
     * participants in {@code memberOrder}. The result is ordered by {@code memberOrder} and sums to {@code amount}.
     */
    public static LinkedHashMap<String, BigDecimal> of(BigDecimal amount, Split split, List<String> memberOrder) {
        List<String> people = split.people();
        var shares = new LinkedHashMap<String, BigDecimal>();
        for (String member : memberOrder) {
            if (people.contains(member)) {
                shares.put(member, rawShare(amount, split, member, people.size()).setScale(2, RoundingMode.DOWN));
            }
        }
        if (shares.isEmpty()) {
            return shares;
        }
        BigDecimal sum = shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        long leftoverCents = amount.subtract(sum).movePointRight(2).longValueExact();
        List<String> order = List.copyOf(shares.keySet());
        for (long i = 0; i < leftoverCents; i++) {
            shares.merge(order.get((int) (i % order.size())), CENT, BigDecimal::add);
        }
        return shares;
    }

    private static BigDecimal rawShare(BigDecimal amount, Split split, String member, int count) {
        return switch (split) {
            case Split.Equal e -> amount.divide(BigDecimal.valueOf(count), 10, RoundingMode.DOWN);
            case Split.Percent p -> amount.multiply(p.percentages().get(member)).divide(HUNDRED, 10, RoundingMode.DOWN);
        };
    }
}
