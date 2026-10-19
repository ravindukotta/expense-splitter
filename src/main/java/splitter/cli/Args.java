package splitter.cli;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import splitter.domain.Money;
import splitter.domain.SplitSpec;
import splitter.domain.SplitterException;

final class Args {
    private Args() {
    }

    static SplitSpec with(String text) {
        if (text.trim().equalsIgnoreCase("everyone")) {
            return new SplitSpec.Everyone();
        }
        List<String> names = new ArrayList<>();
        for (String part : text.split(",")) {
            if (!part.isBlank()) {
                names.add(part.trim());
            }
        }
        if (names.isEmpty()) {
            throw new SplitterException("--with needs at least one name");
        }
        return new SplitSpec.Equal(List.copyOf(names));
    }

    static SplitSpec.Percent split(String text) {
        var percentages = new LinkedHashMap<String, BigDecimal>();
        for (String pair : text.split(",", -1)) {
            int eq = pair.indexOf('=');
            String name = eq < 0 ? "" : pair.substring(0, eq).trim();
            String value = eq < 0 ? "" : pair.substring(eq + 1).trim();
            if (name.isEmpty() || value.isEmpty()) {
                throw new SplitterException("Invalid --split entry '" + pair + "'; expected Name=percent");
            }
            if (percentages.put(name, Money.parsePercent(value)) != null) {
                throw new SplitterException(name + " is listed twice");
            }
        }
        return new SplitSpec.Percent(percentages);
    }
}
