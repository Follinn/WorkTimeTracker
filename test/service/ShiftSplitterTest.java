package service;

import model.Employee;
import model.Shift;
import model.TimeSegment;
import model.TimeType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static model.TimeType.*;
import static org.junit.jupiter.api.Assertions.*;

class ShiftSplitterTest {
    private final ZoneId zone = ZoneId.of("Europe/Moscow");
    private final Employee employee = new Employee(1, "Анна", 30_000);
    private final ShiftSplitter splitter = new ShiftSplitter(zone, Set.of(), Duration.ofHours(8));

    @Test
    void rejectsInvalidSettings() {
        assertThrows(NullPointerException.class,
                () -> new ShiftSplitter(null, Set.of(), Duration.ofHours(8)));
        assertThrows(NullPointerException.class,
                () -> new ShiftSplitter(zone, null, Duration.ofHours(8)));
        assertThrows(NullPointerException.class,
                () -> new ShiftSplitter(zone, Set.of(), null));
        assertThrows(NullPointerException.class,
                () -> new ShiftSplitter(zone, new HashSet<>(Arrays.asList((LocalDate) null)), Duration.ofHours(8)));
        assertThrows(IllegalArgumentException.class,
                () -> new ShiftSplitter(zone, Set.of(), Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new ShiftSplitter(zone, Set.of(), Duration.ofSeconds(-1)));
    }

    @Test
    void emptyInputProducesNoSegments() {
        assertTrue(splitter.split(List.of()).isEmpty());
    }

    @Test
    void rejectsNullInputAndElements() {
        assertThrows(NullPointerException.class, () -> splitter.split(null));
        assertThrows(NullPointerException.class,
                () -> splitter.split(Arrays.asList(shift("2026-09-25T09:00", "2026-09-25T10:00"), null)));
    }

    @Test
    void rejectsDifferentEmployees() {
        Shift first = shift("2026-09-25T09:00", "2026-09-25T10:00");
        Shift second = new Shift(new Employee(2, "Иван", 20_000),
                local("2026-09-25T11:00"), local("2026-09-25T12:00"));
        assertThrows(IllegalArgumentException.class, () -> splitter.split(List.of(first, second)));
    }

    @Test
    void rejectsOverlappingShiftsAfterSorting() {
        Shift first = shift("2026-09-25T09:00", "2026-09-25T12:00");
        Shift second = shift("2026-09-25T11:00", "2026-09-25T13:00");
        assertThrows(IllegalArgumentException.class, () -> splitter.split(List.of(second, first)));
        assertThrows(IllegalArgumentException.class, () -> splitter.split(List.of(first, first)));
    }

    @Test
    void sortsWithoutChangingInputAndReturnsImmutableList() {
        Shift first = shift("2026-09-25T09:00", "2026-09-25T10:00");
        Shift second = shift("2026-09-25T11:00", "2026-09-25T13:00");
        List<Shift> input = new ArrayList<>(List.of(second, first));
        List<TimeSegment> result = splitter.split(input);
        assertParts(result, new TimeType[]{REGULAR, REGULAR}, 60, 120);
        assertEquals(first.getStart(), result.get(0).getStart());
        assertEquals(second.getStart(), result.get(1).getStart());
        assertEquals(List.of(second, first), input);
        assertThrows(UnsupportedOperationException.class, result::clear);
    }

    @Test
    void touchingShiftsWithSameEmployeeIdShareThreshold() {
        Shift first = shift("2026-09-25T09:00", "2026-09-25T13:00");
        Shift second = new Shift(new Employee(1, "Анна", 30_000),
                local("2026-09-25T13:00"), local("2026-09-25T18:00"));
        List<TimeSegment> result = splitter.split(List.of(second, first));
        assertParts(result, new TimeType[]{REGULAR, REGULAR, OVERTIME}, 240, 240, 60);
        assertEquals(local("2026-09-25T17:00"), result.get(2).getStart());
    }

    @Test
    void nightStartsAt22AndEndsAt06() {
        List<TimeSegment> result = splitter.split(List.of(
                shift("2026-09-25T05:00", "2026-09-25T07:00"),
                shift("2026-09-25T21:00", "2026-09-25T23:00")));
        assertParts(result, new TimeType[]{NIGHT, REGULAR, REGULAR, NIGHT}, 60, 60, 60, 60);
        assertEquals(local("2026-09-25T06:00"), result.get(1).getStart());
        assertEquals(local("2026-09-25T22:00"), result.get(3).getStart());
    }

    @Test
    void exactNightBoundariesDoNotCreateEmptySegments() {
        List<TimeSegment> result = splitter.split(List.of(
                shift("2026-09-25T00:00", "2026-09-25T06:00"),
                shift("2026-09-25T22:00", "2026-09-26T00:00")));
        assertParts(result, new TimeType[]{NIGHT, NIGHT}, 360, 120);
    }

    @Test
    void equalityToDailyThresholdIsNotOvertime() {
        assertParts(splitter.split(List.of(shift("2026-09-25T09:00", "2026-09-25T17:00"))),
                new TimeType[]{REGULAR}, 480);
    }

    @Test
    void breaksAreExcludedAndOvertimeHasPriorityOverNight() {
        List<TimeSegment> result = splitter.split(List.of(
                shift("2026-09-25T18:00", "2026-09-25T23:00"),
                shift("2026-09-25T09:00", "2026-09-25T13:00")));
        assertParts(result, new TimeType[]{REGULAR, REGULAR, OVERTIME}, 240, 240, 60);
        assertEquals(local("2026-09-25T13:00"), result.get(0).getEnd());
        assertEquals(local("2026-09-25T18:00"), result.get(1).getStart());
        assertEquals(local("2026-09-25T22:00"), result.get(2).getStart());
    }

    @Test
    void nightHoursAlsoCountTowardDailyThreshold() {
        List<TimeSegment> result = splitter.split(List.of(
                shift("2026-09-25T00:00", "2026-09-25T06:00"),
                shift("2026-09-25T09:00", "2026-09-25T12:00")));
        assertParts(result, new TimeType[]{NIGHT, REGULAR, OVERTIME}, 360, 120, 60);
        assertEquals(local("2026-09-25T11:00"), result.get(2).getStart());
    }

    @Test
    void customThresholdPreservesSecondsAndNanoseconds() {
        ShiftSplitter custom = new ShiftSplitter(zone, Set.of(), Duration.ofSeconds(1).plusNanos(500));
        ZonedDateTime start = local("2026-09-25T09:00");
        List<TimeSegment> result = custom.split(List.of(new Shift(employee, start, start.plusSeconds(2))));
        assertEquals(2, result.size());
        assertEquals(REGULAR, result.get(0).getType());
        assertEquals(Duration.ofSeconds(1).plusNanos(500), result.get(0).getDuration());
        assertEquals(OVERTIME, result.get(1).getType());
        assertEquals(Duration.ofNanos(999_999_500), result.get(1).getDuration());
        assertEquals(start.plusSeconds(1).plusNanos(500), result.get(1).getStart());
    }

    @Test
    void veryLargeThresholdDoesNotOverflowDate() {
        ShiftSplitter custom = new ShiftSplitter(zone, Set.of(), Duration.ofSeconds(Long.MAX_VALUE));
        assertParts(custom.split(List.of(shift("2026-09-25T09:00", "2026-09-25T10:00"))),
                new TimeType[]{REGULAR}, 60);
    }

    @Test
    void holidayHasPriorityOverNightAndOvertime() {
        ShiftSplitter holiday = new ShiftSplitter(zone, Set.of(LocalDate.of(2026, 9, 25)), Duration.ofHours(8));
        List<TimeSegment> result = holiday.split(List.of(shift("2026-09-25T14:00", "2026-09-25T23:00")));
        assertParts(result, new TimeType[]{HOLIDAY, HOLIDAY}, 480, 60);
    }

    @Test
    void holidaysAreCopiedAtConstruction() {
        Set<LocalDate> holidays = new HashSet<>(Set.of(LocalDate.of(2026, 9, 25)));
        ShiftSplitter custom = new ShiftSplitter(zone, holidays, Duration.ofHours(8));
        holidays.clear();
        assertParts(custom.split(List.of(shift("2026-09-25T09:00", "2026-09-25T10:00"))),
                new TimeType[]{HOLIDAY}, 60);
    }

    @Test
    void midnightResetsThresholdAtMonthBoundary() {
        List<TimeSegment> result = splitter.split(List.of(shift("2026-09-30T14:00", "2026-10-01T02:00")));
        assertParts(result, new TimeType[]{REGULAR, OVERTIME, NIGHT}, 480, 120, 120);
        assertEquals(local("2026-10-01T00:00"), result.get(2).getStart());
    }

    @Test
    void holidayStartsAndEndsAtLocalMidnight() {
        ShiftSplitter holiday = new ShiftSplitter(zone, Set.of(LocalDate.of(2026, 10, 1)), Duration.ofHours(8));
        List<TimeSegment> result = holiday.split(List.of(
                shift("2026-09-30T23:00", "2026-10-01T01:00"),
                shift("2026-10-01T23:00", "2026-10-02T01:00")));
        assertParts(result, new TimeType[]{NIGHT, HOLIDAY, HOLIDAY, NIGHT}, 60, 60, 60, 60);
        assertEquals(local("2026-10-01T00:00"), result.get(1).getStart());
        assertEquals(local("2026-10-02T00:00"), result.get(3).getStart());
    }

    @Test
    void localDatesAndNightBoundariesUseConfiguredZone() {
        ShiftSplitter holiday = new ShiftSplitter(zone, Set.of(LocalDate.of(2026, 10, 1)), Duration.ofHours(8));
        Shift utcShift = new Shift(employee, ZonedDateTime.parse("2026-09-30T18:00Z"),
                ZonedDateTime.parse("2026-09-30T22:00Z"));
        List<TimeSegment> result = holiday.split(List.of(utcShift));
        assertParts(result, new TimeType[]{REGULAR, NIGHT, HOLIDAY}, 60, 120, 60);
        assertEquals(local("2026-09-30T21:00"), result.get(0).getStart());
        assertEquals(local("2026-10-01T00:00"), result.get(2).getStart());
        assertTrue(result.stream().allMatch(segment -> segment.getStart().getZone().equals(zone)));
    }

    @Test
    void springTransitionCountsFiveNightHoursAndActualOvertime() {
        List<TimeSegment> result = berlinSplit("2026-03-29T00:00+01:00[Europe/Berlin]",
                "2026-03-29T10:00+02:00[Europe/Berlin]");
        // Ночь длится 5 часов, затем 3 обычных часа и 1 сверхурочный.
        assertParts(result, new TimeType[]{NIGHT, NIGHT, REGULAR, OVERTIME}, 120, 180, 180, 60);
        assertEquals(ZonedDateTime.parse("2026-03-29T09:00+02:00[Europe/Berlin]"), result.get(3).getStart());
    }

    @Test
    void autumnTransitionCountsSevenNightHoursAndActualOvertime() {
        List<TimeSegment> result = berlinSplit("2026-10-25T00:00+02:00[Europe/Berlin]",
                "2026-10-25T10:00+01:00[Europe/Berlin]");
        // Повторённый час добавляет час работы: ночь длится 7 часов.
        assertParts(result, new TimeType[]{NIGHT, NIGHT, REGULAR, OVERTIME}, 180, 240, 60, 180);
        assertEquals(ZonedDateTime.parse("2026-10-25T07:00+01:00[Europe/Berlin]"), result.get(3).getStart());
    }

    @Test
    void repeatedLocalTimeStillHasPositiveActualDuration() {
        List<TimeSegment> result = berlinSplit("2026-10-25T02:30+02:00[Europe/Berlin]",
                "2026-10-25T02:30+01:00[Europe/Berlin]");
        assertParts(result, new TimeType[]{NIGHT, NIGHT}, 30, 30);
        assertEquals(result.get(0).getEnd(), result.get(1).getStart());
    }

    @Test
    void separateCallsDoNotShareWorkedHours() {
        List<Shift> shifts = List.of(shift("2026-09-25T09:00", "2026-09-25T14:00"));
        assertParts(splitter.split(shifts), new TimeType[]{REGULAR}, 300);
        assertParts(splitter.split(shifts), new TimeType[]{REGULAR}, 300);
    }

    private ZonedDateTime local(String value) {
        return LocalDateTime.parse(value).atZone(zone);
    }

    private Shift shift(String start, String end) {
        return new Shift(employee, local(start), local(end));
    }

    private List<TimeSegment> berlinSplit(String start, String end) {
        ShiftSplitter berlin = new ShiftSplitter(ZoneId.of("Europe/Berlin"), Set.of(), Duration.ofHours(8));
        return berlin.split(List.of(new Shift(employee, ZonedDateTime.parse(start), ZonedDateTime.parse(end))));
    }

    private void assertParts(List<TimeSegment> actual, TimeType[] types, long... minutes) {
        assertEquals(types.length, actual.size());
        assertEquals(types.length, minutes.length);
        for (int i = 0; i < types.length; i++) {
            assertEquals(types[i], actual.get(i).getType(), "Тип отрезка " + i);
            assertEquals(Duration.ofMinutes(minutes[i]), actual.get(i).getDuration(), "Длительность отрезка " + i);
        }
    }
}
