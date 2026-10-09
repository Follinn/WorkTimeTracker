package model;

import org.junit.jupiter.api.Test;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MonthlyTimesheetTest {
    private final ZoneId zone = ZoneId.of("Europe/Moscow");
    private final YearMonth month = YearMonth.of(2026, 9);
    private final MonthlyTimesheet sheet = new MonthlyTimesheet(month, zone);
    private final Employee employee = new Employee(1, "Анна", 30_000);
    private final ZonedDateTime start = month.atDay(25).atTime(9, 0).atZone(zone);

    @Test
    void emptySheetStoresMonthAndZone() {
        assertEquals(month, sheet.getMonth());
        assertEquals(zone, sheet.getZone());
        assertTrue(sheet.getShifts().isEmpty());
        assertTrue(sheet.getShiftsByEmployee(1).isEmpty());
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(NullPointerException.class, () -> new MonthlyTimesheet(null, zone));
        assertThrows(NullPointerException.class, () -> new MonthlyTimesheet(month, null));
        assertThrows(NullPointerException.class, () -> sheet.addShift(null));
    }

    @Test
    void rejectsAllOverlapShapesWithoutChangingSheet() {
        Shift original = new Shift(employee, start, start.plusHours(4));
        sheet.addShift(original);
        // Пересечения слева и справа, вложение, охват и дубликат.
        for (int[] bounds : new int[][]{{-1, 1}, {3, 5}, {1, 3}, {-1, 5}, {0, 4}}) {
            Shift overlap = new Shift(employee, start.plusHours(bounds[0]), start.plusHours(bounds[1]));
            assertThrows(IllegalArgumentException.class, () -> sheet.addShift(overlap));
        }
        assertEquals(List.of(original), sheet.getShifts());
    }

    @Test
    void allowsTouchingShiftsOnBothSides() {
        sheet.addShift(new Shift(employee, start, start.plusHours(4)));
        sheet.addShift(new Shift(employee, start.minusHours(2), start));
        sheet.addShift(new Shift(employee, start.plusHours(4), start.plusHours(6)));
        assertEquals(3, sheet.getShifts().size());
    }

    @Test
    void allowsDifferentEmployeesAndFiltersById() {
        Employee other = new Employee(2, "Иван", 20_000);
        Shift first = new Shift(employee, start, start.plusHours(4));
        Shift second = new Shift(other, start, start.plusHours(4));
        sheet.addShift(first);
        sheet.addShift(second);
        assertEquals(List.of(first), sheet.getShiftsByEmployee(1));
        assertEquals(List.of(second), sheet.getShiftsByEmployee(2));
        assertTrue(sheet.getShiftsByEmployee(3).isEmpty());
    }

    @Test
    void sameIdMeansSameEmployeeEvenForDifferentObjects() {
        sheet.addShift(new Shift(employee, start, start.plusHours(4)));
        Employee sameId = new Employee(1, "Анна Иванова", 35_000);
        assertThrows(IllegalArgumentException.class,
                () -> sheet.addShift(new Shift(sameId, start, start.plusHours(1))));
    }

    @Test
    void overlapUsesInstantsAcrossZones() {
        sheet.addShift(new Shift(employee, start, start.plusHours(4)));
        ZonedDateTime otherZone = start.withZoneSameInstant(ZoneId.of("UTC"));
        assertThrows(IllegalArgumentException.class,
                () -> sheet.addShift(new Shift(employee, otherZone, otherZone.plusHours(1))));
    }

    @Test
    void acceptsShiftsCrossingEitherMonthBoundary() {
        ZonedDateTime firstDay = month.atDay(1).atStartOfDay(zone);
        ZonedDateTime nextMonth = month.plusMonths(1).atDay(1).atStartOfDay(zone);
        Shift incoming = new Shift(employee, firstDay.minusHours(2), firstDay.plusHours(2));
        Shift outgoing = new Shift(employee, nextMonth.minusHours(2), nextMonth.plusHours(2));
        sheet.addShift(incoming);
        sheet.addShift(outgoing);
        assertEquals(List.of(incoming, outgoing), sheet.getShifts());
    }

    @Test
    void rejectsShiftsOutsideMonthIncludingTouchingBoundaries() {
        ZonedDateTime firstDay = month.atDay(1).atStartOfDay(zone);
        ZonedDateTime nextMonth = month.plusMonths(1).atDay(1).atStartOfDay(zone);
        for (Shift outside : List.of(
                new Shift(employee, firstDay.minusHours(2), firstDay),
                new Shift(employee, nextMonth, nextMonth.plusHours(2)),
                new Shift(employee, firstDay.minusDays(3), firstDay.minusDays(2)),
                new Shift(employee, nextMonth.plusDays(2), nextMonth.plusDays(3)))) {
            assertThrows(IllegalArgumentException.class, () -> sheet.addShift(outside));
        }
        assertTrue(sheet.getShifts().isEmpty());
    }

    @Test
    void monthMembershipUsesTimesheetZone() {
        // В UTC ещё август, а в Москве уже сентябрь.
        ZonedDateTime utcStart = ZonedDateTime.parse("2026-08-31T22:00Z");
        Shift shift = new Shift(employee, utcStart, utcStart.plusHours(1));
        sheet.addShift(shift);
        assertEquals(List.of(shift), sheet.getShifts());
    }

    @Test
    void returnedListsAreImmutableSnapshots() {
        Shift first = new Shift(employee, start, start.plusHours(1));
        sheet.addShift(first);
        List<Shift> all = sheet.getShifts();
        List<Shift> selected = sheet.getShiftsByEmployee(1);
        assertThrows(UnsupportedOperationException.class, () -> all.add(first));
        assertThrows(UnsupportedOperationException.class, selected::clear);
        sheet.addShift(new Shift(employee, start.plusHours(2), start.plusHours(3)));
        assertEquals(List.of(first), all);
        assertEquals(List.of(first), selected);
        assertEquals(2, sheet.getShifts().size());
    }
}
