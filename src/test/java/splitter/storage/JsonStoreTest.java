package splitter.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import splitter.domain.Ledger;
import splitter.domain.SplitSpec;
import splitter.domain.SplitterException;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonStoreTest {
    @TempDir Path dir;

    @Test void missingFileLoadsEmptyLedger() {
        assertEquals(List.of(), new JsonStore(dir.resolve("none.json")).load().expenses());
    }

    @Test void roundTripsEverything() {
        var ledger = new Ledger();
        for (var n : List.of("Alex", "Sam", "Jo")) ledger.addMember(n);
        var pct = new LinkedHashMap<String, BigDecimal>();
        pct.put("Alex", new BigDecimal("40"));
        pct.put("Sam", new BigDecimal("60"));
        ledger.addExpense("Rent", new BigDecimal("1000.00"), "Sam", new SplitSpec.Percent(pct), LocalDate.of(2026, 10, 1));
        ledger.addExpense("Tickets", new BigDecimal("30.00"), "Alex", new SplitSpec.Equal(List.of("Alex", "Jo")), LocalDate.of(2026, 10, 2));
        ledger.recordPayment("Jo", "Sam", LocalDate.of(2026, 10, 3));
        var store = new JsonStore(dir.resolve("sub/data.json"));
        store.save(ledger);
        var loaded = store.load();
        assertEquals(ledger.snapshot(), loaded.snapshot());
        assertEquals(ledger.suggestedPayments(), loaded.suggestedPayments());
        assertFalse(Files.exists(dir.resolve("sub/data.json.tmp")));
    }

    @Test void corruptFileFailsAndIsLeftUntouched() throws IOException {
        var file = dir.resolve("data.json");
        Files.writeString(file, "{not json");
        var ex = assertThrows(SplitterException.class, () -> new JsonStore(file).load());
        assertTrue(ex.getMessage().startsWith("Could not read data file " + file));
        assertEquals("{not json", Files.readString(file));
    }
}
