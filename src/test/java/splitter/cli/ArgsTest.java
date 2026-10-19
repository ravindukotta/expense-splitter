package splitter.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import splitter.domain.SplitSpec;
import splitter.domain.SplitterException;

class ArgsTest {
    @Test void everyoneKeyword() { assertEquals(new SplitSpec.Everyone(), Args.with("Everyone")); }
    @Test void namesTrimmed() { assertEquals(new SplitSpec.Equal(List.of("Alex", "Sam")), Args.with(" Alex , Sam ")); }
    @Test void emptyWithRejected() { assertThrows(SplitterException.class, () -> Args.with(" , ")); }
    @Test void splitParsesPairsInOrder() {
        assertEquals(List.of("Alex", "Sam"), List.copyOf(Args.split("Alex=40,Sam=60").percentages().keySet()));
    }
    @ParameterizedTest @ValueSource(strings = {"Alex", "Alex=", "=40", "Alex=forty"})
    void malformedSplitRejected(String s) { assertThrows(SplitterException.class, () -> Args.split(s)); }
}
