package splitter.domain;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

/** How an expense is divided. Names are always canonical member names. */
public sealed interface Split permits Split.Equal, Split.Percent {

    List<String> people();

    record Equal(List<String> people) implements Split {
    }

    record Percent(LinkedHashMap<String, BigDecimal> percentages) implements Split {
        @Override
        public List<String> people() {
            return List.copyOf(percentages.keySet());
        }
    }
}
