package service;

import model.Employee;
import model.MonthlyReport;
import model.MonthlyTimesheet;
import model.Shift;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static model.TimeType.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest {
    private final ZoneId zone = ZoneId.of("Europe/Moscow");
    private final Employee employee = new Employee(1, "Анна", 10_000);
    private final ReportService service = new ReportService(Set.of(LocalDate.of(2026, 9, 26)), Duration.ofHours(8));

    @Test
    void reportsAllTypesAndOnlySelectedEmployee() {
        MonthlyTimesheet sheet = new MonthlyTimesheet(YearMonth.of(2026, 9), zone);
        sheet.addShift(shift(employee, "2026-09-25T05:00+03:00", "2026-09-25T07:00+03:00"));
        sheet.addShift(shift(employee, "2026-09-25T12:00+03:00", "2026-09-25T20:00+03:00"));
        sheet.addShift(shift(employee, "2026-09-26T09:00+03:00", "2026-09-26T12:00+03:00"));
        sheet.addShift(shift(new Employee(2, "Иван", 99_000),
                "2026-09-25T05:00+03:00", "2026-09-25T20:00+03:00"));
        MonthlyReport report = service.generateReport(sheet, employee);
        assertEquals(Map.of(REGULAR, Duration.ofHours(7), OVERTIME, Duration.ofHours(2),
                NIGHT, Duration.ofHours(1), HOLIDAY, Duration.ofHours(3)), report.getTimeByType());
        assertEquals(Duration.ofHours(13), report.getTotalDuration());
        // 7*10000 + 2*15000 + 1*12000 + 3*20000.
        assertEquals(172_000, report.getPaymentKopecks());
        assertEquals(sheet.getMonth(), report.getMonth());
        assertEquals(zone, report.getZone());
        assertSame(employee, report.getEmployee());
    }

    @Test
    void emptySheetAndEmployeeWithoutShiftsProduceZeroReports() {
        MonthlyTimesheet sheet = new MonthlyTimesheet(YearMonth.of(2026, 9), zone);
        assertEquals(0, service.generateReport(sheet, employee).getPaymentKopecks());
        sheet.addShift(shift(new Employee(2, "Иван", 100),
                "2026-09-25T09:00+03:00", "2026-09-25T10:00+03:00"));
        MonthlyReport report = service.generateReport(sheet, employee);
        assertEquals(Duration.ZERO, report.getTotalDuration());
        assertTrue(report.getTimeByType().values().stream().allMatch(Duration.ZERO::equals));
        assertEquals(0, report.getPaymentKopecks());
    }

    @Test
    void clipsBothMonthBoundariesUsingTimesheetZone() {
        MonthlyTimesheet sheet = new MonthlyTimesheet(YearMonth.of(2026, 9), zone);
        // В Москве: 31 августа 23:00–1 сентября 02:00 и 30 сентября 23:00–1 октября 02:00.
        sheet.addShift(shift(employee, "2026-08-31T20:00Z", "2026-08-31T23:00Z"));
        sheet.addShift(shift(employee, "2026-09-30T20:00Z", "2026-09-30T23:00Z"));
        MonthlyReport report = service.generateReport(sheet, employee);
        assertEquals(Duration.ofHours(3), report.getTotalDuration());
        assertEquals(Duration.ofHours(3), report.getTimeByType().get(NIGHT));
        assertEquals(36_000, report.getPaymentKopecks());
    }

    @Test
    void roundsOnceForWholeMonthAcrossShiftsAndTypes() {
        Employee lowRate = new Employee(1, "Анна", 1);
        MonthlyTimesheet sheet = new MonthlyTimesheet(YearMonth.of(2026, 9), zone);
        sheet.addShift(shift(lowRate, "2026-09-25T09:00+03:00", "2026-09-25T09:15+03:00"));
        sheet.addShift(shift(lowRate, "2026-09-25T22:00+03:00", "2026-09-25T22:15+03:00"));
        assertEquals(1, service.generateReport(sheet, lowRate).getPaymentKopecks());
    }

    @Test
    void usesSuppliedEmployeeRateForMatchingId() {
        MonthlyTimesheet sheet = new MonthlyTimesheet(YearMonth.of(2026, 9), zone);
        sheet.addShift(shift(new Employee(1, "Анна", 100),
                "2026-09-25T09:00+03:00", "2026-09-25T10:00+03:00"));
        assertEquals(10_000, service.generateReport(sheet, employee).getPaymentKopecks());
    }

    @Test
    void springReportUsesActualHours() {
        MonthlyTimesheet sheet = new MonthlyTimesheet(YearMonth.of(2026, 3), ZoneId.of("Europe/Berlin"));
        sheet.addShift(shift(employee, "2026-03-29T00:00+01:00[Europe/Berlin]",
                "2026-03-29T10:00+02:00[Europe/Berlin]"));
        MonthlyReport report = service.generateReport(sheet, employee);
        assertEquals(Duration.ofHours(9), report.getTotalDuration());
        assertEquals(Duration.ofHours(5), report.getTimeByType().get(NIGHT));
        assertEquals(Duration.ofHours(1), report.getTimeByType().get(OVERTIME));
        assertEquals(105_000, report.getPaymentKopecks());
    }

    @Test
    void autumnReportIncludesRepeatedHour() {
        MonthlyTimesheet sheet = new MonthlyTimesheet(YearMonth.of(2026, 10), ZoneId.of("Europe/Berlin"));
        sheet.addShift(shift(employee, "2026-10-25T00:00+02:00[Europe/Berlin]",
                "2026-10-25T10:00+01:00[Europe/Berlin]"));
        MonthlyReport report = service.generateReport(sheet, employee);
        assertEquals(Duration.ofHours(11), report.getTotalDuration());
        assertEquals(Duration.ofHours(7), report.getTimeByType().get(NIGHT));
        assertEquals(Duration.ofHours(3), report.getTimeByType().get(OVERTIME));
        assertEquals(139_000, report.getPaymentKopecks());
    }

    @Test
    void copiesHolidaysAndSupportsCustomThreshold() {
        Set<LocalDate> holidays = new HashSet<>(Set.of(LocalDate.of(2026, 9, 26)));
        ReportService custom = new ReportService(holidays, Duration.ofHours(1));
        holidays.clear();
        MonthlyTimesheet sheet = new MonthlyTimesheet(YearMonth.of(2026, 9), zone);
        sheet.addShift(shift(employee, "2026-09-25T09:00+03:00", "2026-09-25T11:00+03:00"));
        sheet.addShift(shift(employee, "2026-09-26T09:00+03:00", "2026-09-26T11:00+03:00"));
        MonthlyReport report = custom.generateReport(sheet, employee);
        assertEquals(Duration.ofHours(1), report.getTimeByType().get(OVERTIME));
        assertEquals(Duration.ofHours(2), report.getTimeByType().get(HOLIDAY));
        assertEquals(65_000, report.getPaymentKopecks());
    }

    @Test
    void rejectsInvalidSettingsAndNullArguments() {
        assertThrows(NullPointerException.class, () -> new ReportService(null, Duration.ofHours(8)));
        assertThrows(NullPointerException.class, () -> new ReportService(Set.of(), null));
        Set<LocalDate> invalid = new HashSet<>();
        invalid.add(null);
        assertThrows(NullPointerException.class, () -> new ReportService(invalid, Duration.ofHours(8)));
        assertThrows(IllegalArgumentException.class, () -> new ReportService(Set.of(), Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new ReportService(Set.of(), Duration.ofNanos(-1)));
        assertThrows(NullPointerException.class, () -> service.generateReport(null, employee));
        assertThrows(NullPointerException.class,
                () -> service.generateReport(new MonthlyTimesheet(YearMonth.of(2026, 9), zone), null));
    }

    private Shift shift(Employee worker, String start, String end) {
        return new Shift(worker, ZonedDateTime.parse(start), ZonedDateTime.parse(end));
    }
}
