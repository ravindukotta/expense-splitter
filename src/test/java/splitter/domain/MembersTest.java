package splitter.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MembersTest {
    @Test void keepsJoinOrderAndResolvesCaseInsensitively() {
        var m = new Members();
        m.add("Alex"); m.add("Sam"); m.add(" Priya ");
        assertEquals(List.of("Alex", "Sam", "Priya"), m.order());
        assertEquals("Priya", m.resolve("PRIYA"));
    }
    @Test void rejectsDuplicateActiveName() {
        var m = new Members(); m.add("Jo");
        assertThrows(SplitterException.class, () -> m.add("jo"));
    }
    @ParameterizedTest @ValueSource(strings = {"", "   ", "A,B", "A=B", "everyone", "Everyone"})
    void rejectsNamesThatBreakArgumentSyntax(String bad) {
        assertThrows(SplitterException.class, () -> new Members().add(bad));
    }
    @Test void unknownNameErrorNamesTheInput() {
        var ex = assertThrows(SplitterException.class, () -> new Members().resolve("Zed"));
        assertEquals("Unknown member 'Zed'", ex.getMessage());
    }
    @Test void removedMemberResolvesButIsNotActive() {
        var m = new Members(); m.add("Alex"); m.add("Jo");
        m.deactivate("Jo");
        assertEquals("Jo", m.resolve("jo"));
        assertEquals(List.of("Alex"), m.activeNames());
        assertEquals("Jo has been removed from the flat",
            assertThrows(SplitterException.class, () -> m.resolveActive("Jo")).getMessage());
    }
    @Test void reAddingRemovedMemberReactivatesInOriginalPosition() {
        var m = new Members(); m.add("Alex"); m.add("Jo"); m.add("Sam");
        m.deactivate("Jo");
        m.add("JO");
        assertEquals(List.of("Alex", "Jo", "Sam"), m.order());
        assertEquals(List.of("Alex", "Jo", "Sam"), m.activeNames());
    }
}
