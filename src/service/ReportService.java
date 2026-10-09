package service;

import model.Employee;
import model.MonthlyReport;
import model.MonthlyTimesheet;
import model.TimeType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Формирует месячный отчёт сотрудника в часовом поясе табеля.
 * Проверка нормы выполняется отдельно и не ограничивает формирование отчёта.
 */
public final class ReportService {
    private final Set<LocalDate> holidays;
    private final Duration dailyThreshold;

    /**
     * Создаёт сервис с правилами праздничного и сверхурочного времени.
     *
     * @param holidays праздничные даты в часовом поясе табеля
     * @param dailyThreshold положительный дневной порог фактической работы
     * @throws NullPointerException если аргумент или дата праздника равен {@code null}
     * @throws IllegalArgumentException если дневной порог не положителен
     */
    public ReportService(Set<LocalDate> holidays, Duration dailyThreshold) {
        Objects.requireNonNull(holidays, "Праздники не должны быть null");
        for (LocalDate holiday : holidays) {
            Objects.requireNonNull(holiday, "Дата праздника не должна быть null");
        }
        this.holidays = Set.copyOf(holidays);
        this.dailyThreshold = Objects.requireNonNull(dailyThreshold, "Дневной порог не должен быть null");
        if (dailyThreshold.isNegative() || dailyThreshold.isZero()) {
            throw new IllegalArgumentException("Дневной порог должен быть положительным");
        }
    }

    /**
     * Формирует отчёт по сменам сотрудника за месяц табеля.
     * Смены выбираются по ID; ставка переданного сотрудника применяется ко всему
     * месяцу. Части смен за границами месяца не включаются. При отсутствии смен
     * возвращает отчёт с нулевыми длительностями и оплатой.
     *
     * @param timesheet табель, определяющий месяц, часовой пояс и смены
     * @param employee сотрудник отчёта и источник почасовой ставки
     * @return месячный отчёт с общей длительностью, временем по типам и оплатой
     * @throws NullPointerException если аргумент равен {@code null}
     * @throws ArithmeticException если оплата не помещается в {@code long}
     */
    public MonthlyReport generateReport(MonthlyTimesheet timesheet, Employee employee) {
        Objects.requireNonNull(timesheet, "Табель не должен быть null");
        Objects.requireNonNull(employee, "Сотрудник не должен быть null");
        Instant start = timesheet.getMonth().atDay(1).atStartOfDay(timesheet.getZone()).toInstant();
        Instant end = timesheet.getMonth().plusMonths(1).atDay(1)
                .atStartOfDay(timesheet.getZone()).toInstant();

        TimeCalculator timeCalculator = new TimeCalculator(
                new ShiftSplitter(timesheet.getZone(), holidays, dailyThreshold)
        );
        Map<TimeType, Duration> timeByType = timeCalculator.calculateByType(
                timesheet.getShiftsByEmployee(employee.getId()), start, end
        );
        long payment = new PaymentCalculator().calculate(employee, timeByType);
        return new MonthlyReport(employee, timesheet.getMonth(), timesheet.getZone(), timeByType, payment);
    }
}
