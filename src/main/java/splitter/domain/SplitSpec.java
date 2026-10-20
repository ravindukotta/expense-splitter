package splitter.domain;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

/** A split as the user typed it: names are raw input, not yet resolved against the members. */
public sealed interface SplitSpec permits SplitSpec.Everyone, SplitSpec.Equal, SplitSpec.Percent {

    record Everyone() implements SplitSpec {
    }

    record Equal(List<String> names) implements SplitSpec {
    }

    record Percent(LinkedHashMap<String, BigDecimal> percentages) implements SplitSpec {
    }
}
