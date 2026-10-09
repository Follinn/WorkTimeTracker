package model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static model.TimeType.*;
import static org.junit.jupiter.api.Assertions.*;

class MonthlyReportTest {
    private final Employee employee = new Employee(1, "Анна", 100);
    private final YearMonth month = YearMonth.of(2026, 9);
    private final ZoneId zone = ZoneId.of("Europe/Moscow");

    @Test
    void storesMetadataAndCalculatesTotalFromTypes() {
        MonthlyReport report = new MonthlyReport(employee, month, zone,
                Map.of(REGULAR, Duration.ofHours(2), NIGHT, Duration.ofMinutes(30)), 260);
        assertSame(employee, report.getEmployee());
        assertEquals(month, report.getMonth());
        assertEquals(zone, report.getZone());
        assertEquals(Duration.ofMinutes(150), report.getTotalDuration());
        assertEquals(260, report.getPaymentKopecks());
        assertEquals(Map.of(REGULAR, Duration.ofHours(2), NIGHT, Duration.ofMinutes(30),
                HOLIDAY, Duration.ZERO, OVERTIME, Duration.ZERO), report.getTimeByType());
    }

    @Test
    void copiesInputAndExposesImmutableMap() {
        Map<TimeType, Duration> input = new HashMap<>(Map.of(REGULAR, Duration.ofHours(1)));
        MonthlyReport report = new MonthlyReport(employee, month, zone, input, 100);
        input.clear();
        assertEquals(Duration.ofHours(1), report.getTimeByType().get(REGULAR));
        assertThrows(UnsupportedOperationException.class, () -> report.getTimeByType().clear());
    }

    @Test
    void acceptsEmptyReport() {
        MonthlyReport report = new MonthlyReport(employee, month, zone, Map.of(), 0);
        assertEquals(Duration.ZERO, report.getTotalDuration());
        assertEquals(4, report.getTimeByType().size());
        assertTrue(report.getTimeByType().values().stream().allMatch(Duration.ZERO::equals));
        assertEquals(0, report.getPaymentKopecks());
    }

    @Test
    void rejectsNullArgumentsAndMapEntries() {
        assertThrows(NullPointerException.class, () -> new MonthlyReport(null, month, zone, Map.of(), 0));
        assertThrows(NullPointerException.class, () -> new MonthlyReport(employee, null, zone, Map.of(), 0));
        assertThrows(NullPointerException.class, () -> new MonthlyReport(employee, month, null, Map.of(), 0));
        assertThrows(NullPointerException.class, () -> new MonthlyReport(employee, month, zone, null, 0));
        Map<TimeType, Duration> invalid = new HashMap<>();
        invalid.put(null, Duration.ZERO);
        assertThrows(NullPointerException.class, () -> new MonthlyReport(employee, month, zone, invalid, 0));
        invalid.clear();
        invalid.put(REGULAR, null);
        assertThrows(NullPointerException.class, () -> new MonthlyReport(employee, month, zone, invalid, 0));
    }

    @Test
    void rejectsNegativePaymentAndDuration() {
        assertThrows(IllegalArgumentException.class, () -> new MonthlyReport(employee, month, zone, Map.of(), -1));
        assertThrows(IllegalArgumentException.class, () -> new MonthlyReport(employee, month, zone,
                Map.of(REGULAR, Duration.ofNanos(-1)), 0));
    }
}
