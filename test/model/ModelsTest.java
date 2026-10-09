package model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import static org.junit.jupiter.api.Assertions.*;

class ModelsTest {
    private final Employee employee = new Employee(1, "Анна", 30_000);
    private final ZonedDateTime start = ZonedDateTime.parse("2026-09-25T09:00+03:00");

    @Test
    void employeeStoresValuesAndTrimsName() {
        Employee actual = new Employee(7, "  Иван  ", 12_345);
        assertEquals(7, actual.getId());
        assertEquals("Иван", actual.getName());
        assertEquals(12_345, actual.getHourlyRateKopecks());
    }

    @Test
    void employeeRejectsNonPositiveIdAndRate() {
        for (long value : new long[]{0, -1}) {
            assertThrows(IllegalArgumentException.class, () -> new Employee(value, "Анна", 100));
            assertThrows(IllegalArgumentException.class, () -> new Employee(1, "Анна", value));
        }
    }

    @Test
    void employeeRejectsMissingName() {
        assertThrows(NullPointerException.class, () -> new Employee(1, null, 100));
        assertThrows(IllegalArgumentException.class, () -> new Employee(1, "", 100));
        assertThrows(IllegalArgumentException.class, () -> new Employee(1, "   ", 100));
    }

    @Test
    void shiftStoresValuesAndCountsAcrossMidnight() {
        ZonedDateTime end = start.plusDays(1).withHour(1);
        Shift shift = new Shift(employee, start, end);
        assertSame(employee, shift.getEmployee());
        assertEquals(start, shift.getStart());
        assertEquals(end, shift.getEnd());
        assertEquals(Duration.ofHours(16), shift.getDuration());
    }

    @Test
    void shiftRejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> new Shift(null, start, start.plusHours(1)));
        assertThrows(NullPointerException.class, () -> new Shift(employee, null, start));
        assertThrows(NullPointerException.class, () -> new Shift(employee, start, null));
    }

    @Test
    void shiftRejectsEndAtOrBeforeStartInstant() {
        assertThrows(IllegalArgumentException.class, () -> new Shift(employee, start, start));
        assertThrows(IllegalArgumentException.class, () -> new Shift(employee, start, start.minusNanos(1)));
        ZonedDateTime sameInstant = ZonedDateTime.parse("2026-09-25T10:00+04:00");
        assertThrows(IllegalArgumentException.class, () -> new Shift(employee, start, sameInstant));
    }

    @Test
    void shiftCountsActualDurationAcrossOffsets() {
        ZonedDateTime end = ZonedDateTime.parse("2026-09-25T09:00+02:00");
        assertEquals(Duration.ofHours(1), new Shift(employee, start, end).getDuration());
    }

    @Test
    void segmentStoresTypeAndActualDuration() {
        ZonedDateTime end = ZonedDateTime.parse("2026-09-25T09:30+02:00");
        TimeSegment segment = new TimeSegment(start, end, TimeType.NIGHT);
        assertEquals(start, segment.getStart());
        assertEquals(end, segment.getEnd());
        assertEquals(TimeType.NIGHT, segment.getType());
        assertEquals(Duration.ofMinutes(90), segment.getDuration());
    }

    @Test
    void segmentRejectsNullArgumentsAndNonPositiveDuration() {
        assertThrows(NullPointerException.class, () -> new TimeSegment(null, start, TimeType.REGULAR));
        assertThrows(NullPointerException.class, () -> new TimeSegment(start, null, TimeType.REGULAR));
        assertThrows(NullPointerException.class, () -> new TimeSegment(start, start.plusHours(1), null));
        assertThrows(IllegalArgumentException.class, () -> new TimeSegment(start, start, TimeType.REGULAR));
        assertThrows(IllegalArgumentException.class, () -> new TimeSegment(start, start.minusHours(1), TimeType.REGULAR));
    }

    @Test
    void coefficientsMatchEducationalRules() {
        assertEquals(new BigDecimal("1.0"), TimeType.REGULAR.getCoefficient());
        assertEquals(new BigDecimal("1.5"), TimeType.OVERTIME.getCoefficient());
        assertEquals(new BigDecimal("1.2"), TimeType.NIGHT.getCoefficient());
        assertEquals(new BigDecimal("2.0"), TimeType.HOLIDAY.getCoefficient());
    }
}
