package splitter.storage;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import splitter.domain.Ledger;
import splitter.domain.LedgerData;
import splitter.domain.Split;
import splitter.domain.SplitterException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Reads and writes the ledger as a JSON file, replacing it atomically on save. */
public final class JsonStore {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = Split.Equal.class, name = "equal"),
            @JsonSubTypes.Type(value = Split.Percent.class, name = "percent")
    })
    private interface SplitMixIn {
    }

    /** {@code people()} is derived from the percentages, so it must not be written out. */
    @JsonIgnoreProperties("people")
    private interface PercentMixIn {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .addMixIn(Split.class, SplitMixIn.class)
            .addMixIn(Split.Percent.class, PercentMixIn.class);

    private final Path file;

    public JsonStore(Path file) {
        this.file = file;
    }

    public static Path defaultPath() {
        return Path.of(System.getProperty("user.home"), ".splitter", "data.json");
    }

    public Ledger load() {
        if (!Files.exists(file)) {
            return new Ledger();
        }
        try {
            return Ledger.restore(MAPPER.readValue(file.toFile(), LedgerData.class));
        } catch (IOException | RuntimeException e) {
            throw new SplitterException("Could not read data file " + file + ": " + e.getMessage());
        }
    }

    public void save(Ledger ledger) {
        var tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            var parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            MAPPER.writeValue(tmp.toFile(), ledger.snapshot());
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new SplitterException("Could not write data file " + file + ": " + e.getMessage());
        }
    }
}
