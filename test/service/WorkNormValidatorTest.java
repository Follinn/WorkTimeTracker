package service;

import exception.WorkNormExceededException;
import model.Employee;
import model.MonthlyReport;
import model.MonthlyTimesheet;
import model.Shift;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static model.TimeType.OVERTIME;
import static org.junit.jupiter.api.Assertions.*;

class WorkNormValidatorTest {
    private final ZoneId zone = ZoneId.of("Europe/Moscow");
    private final Employee employee = new Employee(1, "Анна", 10_000);
    private final WorkNormValidator validator = new WorkNormValidator(
            new TimeCalculator(new ShiftSplitter(zone, Set.of(), Duration.ofHours(8))));
    private final ZonedDateTime start = ZonedDateTime.parse("2026-09-25T09:00+03:00");
    private final Instant from = start.toInstant();
    private final Instant to = start.plusHours(10).toInstant();

    @Test
    void acceptsWorkBelowOrEqualToNorm() {
        List<Shift> shifts = List.of(new Shift(employee, start, start.plusHours(8)));
        assertDoesNotThrow(() -> validator.validate(shifts, from, to, Duration.ofHours(9)));
        assertDoesNotThrow(() -> validator.validate(shifts, from, to, Duration.ofHours(8)));
    }

    @Test
    void rejectsExcessEvenByOneNanosecond() {
        List<Shift> shifts = List.of(new Shift(employee, start, start.plusHours(8).plusNanos(1)));
        WorkNormExceededException exception = assertThrows(WorkNormExceededException.class,
                () -> validator.validate(shifts, from, to, Duration.ofHours(8)));
        assertTrue(exception.getMessage().contains("Превышена норма рабочего времени"));
    }

    @Test
    void zeroNormAcceptsNoWorkButRejectsAnyWork() {
        assertDoesNotThrow(() -> validator.validate(List.of(), from, to, Duration.ZERO));
        List<Shift> shifts = List.of(new Shift(employee, start, start.plusNanos(1)));
        assertThrows(WorkNormExceededException.class,
                () -> validator.validate(shifts, from, to, Duration.ZERO));
        assertDoesNotThrow(() -> validator.validate(shifts, from, from, Duration.ZERO));
    }

    @Test
    void countsOnlyIntersectionWithPeriod() {
        List<Shift> shifts = List.of(new Shift(employee, start.minusHours(2), start.plusHours(12)));
        assertDoesNotThrow(() -> validator.validate(shifts, from, to, Duration.ofHours(10)));
        assertThrows(WorkNormExceededException.class,
                () -> validator.validate(shifts, from, to, Duration.ofHours(10).minusNanos(1)));
    }

    @Test
    void excludesBreaksAndShiftsTouchingPeriod() {
        List<Shift> shifts = List.of(
                new Shift(employee, start.minusHours(1), start),
                new Shift(employee, start, start.plusHours(2)),
                new Shift(employee, start.plusHours(6), start.plusHours(8)),
                new Shift(employee, start.plusHours(10), start.plusHours(11)));
        assertDoesNotThrow(() -> validator.validate(shifts, from, to, Duration.ofHours(4)));
        assertThrows(WorkNormExceededException.class,
                () -> validator.validate(shifts, from, to, Duration.ofHours(3)));
    }

    @Test
    void holidayCoefficientDoesNotMultiplyWorkedTime() {
        WorkNormValidator holiday = new WorkNormValidator(new TimeCalculator(
                new ShiftSplitter(zone, Set.of(start.toLocalDate()), Duration.ofHours(8))));
        List<Shift> shifts = List.of(new Shift(employee, start, start.plusHours(3)));
        assertDoesNotThrow(() -> holiday.validate(shifts, from, to, Duration.ofHours(3)));
        assertThrows(WorkNormExceededException.class,
                () -> holiday.validate(shifts, from, to, Duration.ofHours(2)));
    }

    @Test
    void springTransitionUsesActualDuration() {
        checkBerlinNorm("2026-03-29T00:00+01:00[Europe/Berlin]",
                "2026-03-29T06:00+02:00[Europe/Berlin]", 5);
    }

    @Test
    void autumnTransitionUsesActualDuration() {
        checkBerlinNorm("2026-10-25T00:00+02:00[Europe/Berlin]",
                "2026-10-25T06:00+01:00[Europe/Berlin]", 7);
    }

    @Test
    void failedValidationDoesNotPreventReportWithOvertime() {
        MonthlyTimesheet sheet = new MonthlyTimesheet(YearMonth.of(2026, 9), zone);
        Shift shift = new Shift(employee, start, start.plusHours(10));
        sheet.addShift(shift);
        assertThrows(WorkNormExceededException.class,
                () -> validator.validate(sheet.getShiftsByEmployee(1), from, to, Duration.ofHours(8)));
        MonthlyReport report = new ReportService(Set.of(), Duration.ofHours(8)).generateReport(sheet, employee);
        assertEquals(Duration.ofHours(10), report.getTotalDuration());
        assertEquals(Duration.ofHours(2), report.getTimeByType().get(OVERTIME));
        assertEquals(110_000, report.getPaymentKopecks());
        assertEquals(List.of(shift), sheet.getShifts());
        // Дневной порог 8 часов и норма выбранного периода 10 часов независимы.
        assertDoesNotThrow(() -> validator.validate(sheet.getShiftsByEmployee(1), from, to, Duration.ofHours(10)));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(NullPointerException.class, () -> new WorkNormValidator(null));
        assertThrows(NullPointerException.class, () -> validator.validate(List.of(), from, to, null));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(List.of(), from, to, Duration.ofNanos(-1)));
        assertThrows(NullPointerException.class, () -> validator.validate(null, from, to, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> validator.validate(List.of(), null, to, Duration.ZERO));
        assertThrows(NullPointerException.class, () -> validator.validate(List.of(), from, null, Duration.ZERO));
        assertThrows(NullPointerException.class,
                () -> validator.validate(Arrays.asList((Shift) null), from, to, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(List.of(), to, from, Duration.ZERO));
    }

    private void checkBerlinNorm(String startText, String endText, long hours) {
        ZonedDateTime begin = ZonedDateTime.parse(startText);
        ZonedDateTime end = ZonedDateTime.parse(endText);
        WorkNormValidator berlin = new WorkNormValidator(new TimeCalculator(
                new ShiftSplitter(ZoneId.of("Europe/Berlin"), Set.of(), Duration.ofHours(8))));
        List<Shift> shifts = List.of(new Shift(employee, begin, end));
        assertDoesNotThrow(() -> berlin.validate(shifts, begin.toInstant(), end.toInstant(), Duration.ofHours(hours)));
        assertThrows(WorkNormExceededException.class,
                () -> berlin.validate(shifts, begin.toInstant(), end.toInstant(), Duration.ofHours(hours).minusNanos(1)));
    }
}
