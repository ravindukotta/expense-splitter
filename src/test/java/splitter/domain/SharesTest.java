package splitter.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SharesTest {
    static final List<String> ORDER = List.of("Alex", "Sam", "Priya", "Jo");

    @Test void equalSplitGivesLeftoverCentsInJoinOrder() {
        var s = Shares.of(new BigDecimal("100.00"), new Split.Equal(List.of("Priya", "Sam", "Alex")), ORDER);
        assertEquals(List.of("Alex", "Sam", "Priya"), List.copyOf(s.keySet()));
        assertEquals(new BigDecimal("33.34"), s.get("Alex"));
        assertEquals(new BigDecimal("33.33"), s.get("Sam"));
        assertEquals(new BigDecimal("33.33"), s.get("Priya"));
    }
    @Test void percentSplitOfRent() {
        var p = new LinkedHashMap<String, BigDecimal>();
        p.put("Alex", new BigDecimal("40")); p.put("Sam", new BigDecimal("30")); p.put("Priya", new BigDecimal("30"));
        var s = Shares.of(new BigDecimal("1200.00"), new Split.Percent(p), ORDER);
        assertEquals(new BigDecimal("480.00"), s.get("Alex"));
        assertEquals(new BigDecimal("360.00"), s.get("Sam"));
        assertEquals(new BigDecimal("360.00"), s.get("Priya"));
    }
    @Test void percentSplitRoundsDownThenHandsOutLeftoverInJoinOrder() {
        var p = new LinkedHashMap<String, BigDecimal>();
        p.put("Jo", new BigDecimal("33.34")); p.put("Sam", new BigDecimal("33.33")); p.put("Alex", new BigDecimal("33.33"));
        var s = Shares.of(new BigDecimal("10.00"), new Split.Percent(p), ORDER);
        assertEquals(new BigDecimal("3.34"), s.get("Alex"));   // 3.333 -> 3.33 + leftover cent (first in join order)
        assertEquals(new BigDecimal("3.33"), s.get("Sam"));
        assertEquals(new BigDecimal("3.33"), s.get("Jo"));     // 3.334 -> 3.33
    }
    @Test void sharesAlwaysSumToAmount() {
        var s = Shares.of(new BigDecimal("0.05"), new Split.Equal(List.of("Alex", "Sam", "Priya", "Jo")), ORDER);
        assertEquals(new BigDecimal("0.05"), s.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(new BigDecimal("0.02"), s.get("Alex"));
    }
}
