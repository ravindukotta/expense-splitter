package splitter.settlement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettlementTest {
    static LinkedHashMap<String, BigDecimal> balances(Object... nameAmount) {
        var map = new LinkedHashMap<String, BigDecimal>();
        for (int i = 0; i < nameAmount.length; i += 2) {
            map.put((String) nameAmount[i], new BigDecimal((String) nameAmount[i + 1]).setScale(2));
        }
        return map;
    }

    @Test void chainCollapsesToOnePayment() {          // Jo owes Sam 20, Sam owes Alex 20
        var t = Settlement.simplify(balances("Alex", "20", "Sam", "0", "Jo", "-20"));
        assertEquals(List.of(new Transfer("Jo", "Alex", new BigDecimal("20.00"))), t);
    }

    @Test void greedyLargestDebtorToLargestCreditor() {
        var t = Settlement.simplify(balances("Alex", "50", "Sam", "10", "Priya", "-40", "Jo", "-20"));
        assertEquals(List.of(
            new Transfer("Priya", "Alex", new BigDecimal("40.00")),
            new Transfer("Jo", "Alex", new BigDecimal("10.00")),
            new Transfer("Jo", "Sam", new BigDecimal("10.00"))), t);
    }

    @Test void tiesBrokenByJoinOrder() {
        var t = Settlement.simplify(balances("Alex", "30", "Sam", "30", "Priya", "-30", "Jo", "-30"));
        assertEquals(List.of(
            new Transfer("Priya", "Alex", new BigDecimal("30.00")),
            new Transfer("Jo", "Sam", new BigDecimal("30.00"))), t);
    }

    @Test void allZeroMeansNoTransfers() {
        assertEquals(List.of(), Settlement.simplify(balances("Alex", "0", "Sam", "0")));
    }
}
