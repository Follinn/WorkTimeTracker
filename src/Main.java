import exception.WorkNormExceededException;
import model.Employee;
import model.MonthlyReport;
import model.MonthlyTimesheet;
import model.Shift;
import model.TimeSegment;
import model.TimeType;
import service.ReportService;
import service.ShiftSplitter;
import service.TimeCalculator;
import service.WorkNormValidator;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * Демонстрирует учёт смен, расчёт за период, проверку нормы и месячный отчёт.
 */
public class Main {

    /**
     * Создаёт экземпляр класса запуска приложения.
     */
    public Main() {
    }

    /**
     * Запускает учебный пример и выводит отрезки работы, проверку нормы и отчёт.
     *
     * @param args аргументы командной строки; в примере не используются
     */
    public static void main(String[] args) {
        ZoneId zone = ZoneId.of("Europe/Moscow");
        LocalDate date = LocalDate.of(2026, 9, 25);

        Employee employee = new Employee(
                1, "Константин", 30_000
        );

        Shift first = new Shift(
                employee,
                ZonedDateTime.of(date, LocalTime.of(9, 0), zone),
                ZonedDateTime.of(date, LocalTime.of(13, 0), zone)
        );

        Shift second = new Shift(
                employee,
                ZonedDateTime.of(date, LocalTime.of(18, 0), zone),
                ZonedDateTime.of(date, LocalTime.of(23, 0), zone)
        );

        Shift overnight = new Shift(
                employee,
                date.plusDays(1).atTime(22, 0).atZone(zone),
                date.plusDays(2).atTime(2, 0).atZone(zone)
        );
        MonthlyTimesheet timesheet = new MonthlyTimesheet(YearMonth.from(date), zone);
        timesheet.addShift(first);
        timesheet.addShift(second);
        timesheet.addShift(overnight);

        // Дата выбрана праздничной только для демонстрации учебной модели.
        Set<LocalDate> holidays = Set.of(date.plusDays(1));
        Duration dailyThreshold = Duration.ofHours(8);
        ShiftSplitter splitter = new ShiftSplitter(zone, holidays, dailyThreshold);
        List<Shift> employeeShifts = timesheet.getShiftsByEmployee(employee.getId());
        List<TimeSegment> segments = splitter.split(employeeShifts);

        System.out.println("Отрезки работы (" + zone + "):");
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd.MM HH:mm XXX");

        for (TimeSegment segment : segments) {
            System.out.printf(
                    "%s–%s | %s | %d мин.%n",
                    segment.getStart().format(format),
                    segment.getEnd().format(format),
                    segment.getType(),
                    segment.getDuration().toMinutes()
            );
        }

        TimeCalculator calculator = new TimeCalculator(splitter);
        Instant periodStart = date.atTime(21, 0).atZone(zone).toInstant();
        Instant periodEnd = date.atTime(23, 0).atZone(zone).toInstant();
        System.out.printf("%nРабота 25 сентября с 21:00 до 23:00: %d мин.%n",
                calculator.calculateTotal(employeeShifts, periodStart, periodEnd).toMinutes());

        Instant monthStart = timesheet.getMonth().atDay(1).atStartOfDay(zone).toInstant();
        Instant monthEnd = timesheet.getMonth().plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();
        Duration norm = Duration.ofHours(12);
        try {
            new WorkNormValidator(calculator).validate(employeeShifts, monthStart, monthEnd, norm);
            System.out.println("Норма соблюдена.");
        } catch (WorkNormExceededException exception) {
            System.out.println(exception.getMessage());
        }

        // Отчёт формируется и после превышения нормы.
        MonthlyReport report = new ReportService(holidays, dailyThreshold)
                .generateReport(timesheet, employee);
        System.out.printf("%nОтчёт за %s: %s%n", report.getMonth(), report.getEmployee().getName());
        System.out.printf("Всего: %d мин.%n", report.getTotalDuration().toMinutes());
        for (TimeType type : TimeType.values()) {
            System.out.printf("%s: %d мин.%n", type, report.getTimeByType().get(type).toMinutes());
        }
        System.out.printf("Оплата: %d коп.%n", report.getPaymentKopecks());
    }
}
