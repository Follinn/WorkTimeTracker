package service;

import model.Employee;
import model.MonthlyTimesheet;
import model.Shift;
import model.TimeType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static model.TimeType.*;
import static org.junit.jupiter.api.Assertions.*;

class TimeCalculatorTest {
    private final ZoneId zone = ZoneId.of("Europe/Moscow");
    private final Employee employee = new Employee(1, "Анна", 30_000);
    private final TimeCalculator calculator = new TimeCalculator(
            new ShiftSplitter(zone, Set.of(LocalDate.of(2026, 10, 1)), Duration.ofHours(8))
    );
    private final Instant dayStart = instant("2026-09-25T00:00");
    private final Instant dayEnd = instant("2026-09-26T00:00");

    @Test
    void emptyTimesheetReturnsZeroForEveryTypeAndTotal() {
        MonthlyTimesheet sheet = new MonthlyTimesheet(YearMonth.of(2026, 9), zone);
        List<Shift> shifts = sheet.getShiftsByEmployee(1);
        assertEquals(minutes(0, 0, 0, 0), calculator.calculateByType(shifts, dayStart, dayEnd));
        assertEquals(Duration.ZERO, calculator.calculateTotal(shifts, dayStart, dayEnd));
    }

    @Test
    void clipsBothEndsOfShift() {
        List<Shift> shifts = List.of(shift("2026-09-25T09:00", "2026-09-25T17:00"));
        Instant from = instant("2026-09-25T10:30");
        Instant to = instant("2026-09-25T12:15");
        assertEquals(minutes(105, 0, 0, 0), calculator.calculateByType(shifts, from, to));
        assertEquals(Duration.ofMinutes(105), calculator.calculateTotal(shifts, from, to));
    }

    @Test
    void includesOnlyIntersectionsAndExcludesBreaks() {
        List<Shift> shifts = List.of(
                shift("2026-09-25T08:00", "2026-09-25T10:00"),
                shift("2026-09-25T12:00", "2026-09-25T14:00"),
                shift("2026-09-25T16:00", "2026-09-25T18:00"));
        Instant from = instant("2026-09-25T09:00");
        Instant to = instant("2026-09-25T17:00");
        assertEquals(minutes(240, 0, 0, 0), calculator.calculateByType(shifts, from, to));
        assertEquals(Duration.ofHours(4), calculator.calculateTotal(shifts, from, to));
    }

    @Test
    void shiftsOutsideOrTouchingPeriodContributeNothing() {
        List<Shift> shifts = List.of(
                shift("2026-09-24T09:00", "2026-09-24T10:00"),
                shift("2026-09-24T23:00", "2026-09-25T00:00"),
                shift("2026-09-26T00:00", "2026-09-26T01:00"),
                shift("2026-09-27T09:00", "2026-09-27T10:00"));
        assertEquals(minutes(0, 0, 0, 0), calculator.calculateByType(shifts, dayStart, dayEnd));
        assertEquals(Duration.ZERO, calculator.calculateTotal(shifts, dayStart, dayEnd));
    }

    @Test
    void emptyPeriodInsideShiftReturnsZero() {
        List<Shift> shifts = List.of(shift("2026-09-25T09:00", "2026-09-25T18:00"));
        Instant point = instant("2026-09-25T12:00");
        assertEquals(minutes(0, 0, 0, 0), calculator.calculateByType(shifts, point, point));
        assertEquals(Duration.ZERO, calculator.calculateTotal(shifts, point, point));
    }

    @Test
    void countsEarlierPartOfSameShiftTowardOvertime() {
        List<Shift> shifts = List.of(shift("2026-09-25T09:00", "2026-09-25T19:00"));
        Instant from = instant("2026-09-25T16:00");
        Instant to = instant("2026-09-25T18:00");
        assertEquals(minutes(60, 60, 0, 0), calculator.calculateByType(shifts, from, to));
        assertEquals(Duration.ofHours(2), calculator.calculateTotal(shifts, from, to));
    }

    @Test
    void earlierShiftOutsidePeriodStillAffectsOvertime() {
        List<Shift> shifts = List.of(
                shift("2026-09-25T18:00", "2026-09-25T23:00"),
                shift("2026-09-25T09:00", "2026-09-25T13:00"));
        Instant from = instant("2026-09-25T21:00");
        Instant to = instant("2026-09-25T23:00");
        assertEquals(minutes(60, 60, 0, 0), calculator.calculateByType(shifts, from, to));
    }

    @Test
    void aggregatesAllTypesAcrossSeveralDays() {
        List<Shift> shifts = List.of(
                shift("2026-09-30T05:00", "2026-09-30T07:00"),
                shift("2026-09-30T12:00", "2026-09-30T20:00"),
                shift("2026-10-01T09:00", "2026-10-01T12:00"));
        Instant from = instant("2026-09-30T00:00");
        Instant to = instant("2026-10-02T00:00");
        assertEquals(minutes(420, 120, 60, 180), calculator.calculateByType(shifts, from, to));
        assertEquals(Duration.ofHours(13), calculator.calculateTotal(shifts, from, to));
    }

    @Test
    void clipsAtMonthBoundaryAndResetsDailyThreshold() {
        List<Shift> shifts = List.of(shift("2026-09-30T14:00", "2026-10-01T02:00"));
        Instant boundary = instant("2026-10-01T00:00");
        assertEquals(minutes(480, 120, 0, 0),
                calculator.calculateByType(shifts, instant("2026-09-01T00:00"), boundary));
        assertEquals(minutes(0, 0, 0, 120),
                calculator.calculateByType(shifts, boundary, instant("2026-11-01T00:00")));
    }

    @Test
    void preservesNanosecondsAndHalfOpenBoundary() {
        ZonedDateTime start = local("2026-09-25T09:00");
        List<Shift> shifts = List.of(new Shift(employee, start, start.plusSeconds(1)));
        Instant from = start.toInstant().plusNanos(1);
        Instant to = start.toInstant().plusNanos(999_999_999);
        assertEquals(Duration.ofNanos(999_999_998), calculator.calculateTotal(shifts, from, to));
        assertEquals(Duration.ofNanos(999_999_998), calculator.calculateByType(shifts, from, to).get(REGULAR));
        assertEquals(Duration.ofNanos(1),
                calculator.calculateTotal(shifts, start.toInstant(), start.toInstant().plusNanos(1)));
    }

    @Test
    void utcPeriodClipsShiftUsingActualInstants() {
        List<Shift> shifts = List.of(shift("2026-09-25T21:00", "2026-09-25T23:00"));
        // 18:30–19:30 UTC соответствует 21:30–22:30 в Москве.
        Instant from = Instant.parse("2026-09-25T18:30:00Z");
        Instant to = Instant.parse("2026-09-25T19:30:00Z");
        assertEquals(minutes(30, 0, 30, 0), calculator.calculateByType(shifts, from, to));
        assertEquals(Duration.ofHours(1), calculator.calculateTotal(shifts, from, to));
    }

    @Test
    void springTransitionUsesActualTimeWithinClippedPeriod() {
        TimeCalculator berlin = new TimeCalculator(
                new ShiftSplitter(ZoneId.of("Europe/Berlin"), Set.of(), Duration.ofHours(8)));
        List<Shift> shifts = List.of(new Shift(employee,
                ZonedDateTime.parse("2026-03-29T00:00+01:00[Europe/Berlin]"),
                ZonedDateTime.parse("2026-03-29T10:00+02:00[Europe/Berlin]")));
        Instant from = ZonedDateTime.parse("2026-03-29T01:30+01:00[Europe/Berlin]").toInstant();
        Instant to = ZonedDateTime.parse("2026-03-29T09:30+02:00[Europe/Berlin]").toInstant();
        assertEquals(minutes(180, 30, 210, 0), berlin.calculateByType(shifts, from, to));
        assertEquals(Duration.ofHours(7), berlin.calculateTotal(shifts, from, to));
    }

    @Test
    void autumnRepeatedHourIsIncludedOnceForEachActualInstant() {
        TimeCalculator berlin = new TimeCalculator(
                new ShiftSplitter(ZoneId.of("Europe/Berlin"), Set.of(), Duration.ofHours(8)));
        List<Shift> shifts = List.of(new Shift(employee,
                ZonedDateTime.parse("2026-10-25T00:00+02:00[Europe/Berlin]"),
                ZonedDateTime.parse("2026-10-25T10:00+01:00[Europe/Berlin]")));
        Instant from = ZonedDateTime.parse("2026-10-25T02:30+02:00[Europe/Berlin]").toInstant();
        Instant to = ZonedDateTime.parse("2026-10-25T02:30+01:00[Europe/Berlin]").toInstant();
        assertEquals(minutes(0, 0, 60, 0), berlin.calculateByType(shifts, from, to));
        assertEquals(Duration.ofHours(1), berlin.calculateTotal(shifts, from, to));
    }

    @Test
    void returnsImmutableIndependentResults() {
        List<Shift> shifts = List.of(shift("2026-09-25T09:00", "2026-09-25T10:00"));
        Map<TimeType, Duration> result = calculator.calculateByType(shifts, dayStart, dayEnd);
        assertThrows(UnsupportedOperationException.class, () -> result.put(REGULAR, Duration.ZERO));
        assertEquals(minutes(0, 0, 0, 0), calculator.calculateByType(List.of(), dayStart, dayEnd));
        assertEquals(minutes(60, 0, 0, 0), result);
    }

    @Test
    void rejectsNullArgumentsAndReversedPeriod() {
        assertThrows(NullPointerException.class, () -> new TimeCalculator(null));
        assertThrows(NullPointerException.class, () -> calculator.calculateByType(null, dayStart, dayEnd));
        assertThrows(NullPointerException.class, () -> calculator.calculateByType(List.of(), null, dayEnd));
        assertThrows(NullPointerException.class, () -> calculator.calculateByType(List.of(), dayStart, null));
        assertThrows(NullPointerException.class,
                () -> calculator.calculateByType(Arrays.asList((Shift) null), dayStart, dayEnd));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateByType(List.of(), dayEnd, dayStart));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateTotal(List.of(), dayEnd, dayStart));
    }

    @Test
    void rejectsOverlappingShiftsAndDifferentEmployees() {
        Shift first = shift("2026-09-25T09:00", "2026-09-25T12:00");
        Shift second = shift("2026-09-25T11:00", "2026-09-25T13:00");
        Shift other = new Shift(new Employee(2, "Иван", 20_000),
                local("2026-09-25T14:00"), local("2026-09-25T15:00"));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateByType(List.of(first, second), dayStart, dayEnd));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculateTotal(List.of(first, other), dayStart, dayEnd));
    }

    private ZonedDateTime local(String value) {
        return LocalDateTime.parse(value).atZone(zone);
    }

    private Instant instant(String value) {
        return local(value).toInstant();
    }

    private Shift shift(String start, String end) {
        return new Shift(employee, local(start), local(end));
    }

    private Map<TimeType, Duration> minutes(long regular, long overtime, long night, long holiday) {
        return Map.of(REGULAR, Duration.ofMinutes(regular), OVERTIME, Duration.ofMinutes(overtime),
                NIGHT, Duration.ofMinutes(night), HOLIDAY, Duration.ofMinutes(holiday));
    }
}
