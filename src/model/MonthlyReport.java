package model;

import java.time.Duration;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Неизменяемый месячный отчёт сотрудника с длительностями и оплатой в копейках.
 */
public final class MonthlyReport {
    private final Employee employee;
    private final YearMonth month;
    private final ZoneId zone;
    private final Map<TimeType, Duration> timeByType;
    private final Duration totalDuration;
    private final long paymentKopecks;

    /**
     * Создаёт отчёт, копируя длительности и вычисляя их общую сумму.
     * Отсутствующие типы дополняются нулевыми длительностями.
     *
     * @param employee сотрудник отчёта
     * @param month месяц отчёта
     * @param zone часовой пояс границ месяца
     * @param timeByType длительности по типам рабочего времени
     * @param paymentKopecks рассчитанная оплата в копейках
     * @throws NullPointerException если ссылочный аргумент, ключ или значение карты равен {@code null}
     * @throws IllegalArgumentException если длительность или оплата отрицательная
     * @throws ArithmeticException если сумма длительностей превышает диапазон {@link Duration}
     */
    public MonthlyReport(Employee employee, YearMonth month, ZoneId zone,
                         Map<TimeType, Duration> timeByType, long paymentKopecks) {
        this.employee = Objects.requireNonNull(employee, "Сотрудник не должен быть null");
        this.month = Objects.requireNonNull(month, "Месяц не должен быть null");
        this.zone = Objects.requireNonNull(zone, "Часовой пояс не должен быть null");
        Objects.requireNonNull(timeByType, "Карта длительностей не должна быть null");
        if (paymentKopecks < 0) {
            throw new IllegalArgumentException("Оплата не должна быть отрицательной");
        }

        Map<TimeType, Duration> copy = new EnumMap<>(TimeType.class);
        for (TimeType type : TimeType.values()) {
            copy.put(type, Duration.ZERO);
        }
        Duration total = Duration.ZERO;
        for (Map.Entry<TimeType, Duration> entry : timeByType.entrySet()) {
            TimeType type = Objects.requireNonNull(entry.getKey(), "Тип времени не должен быть null");
            Duration duration = Objects.requireNonNull(entry.getValue(), "Длительность не должна быть null");
            if (duration.isNegative()) {
                throw new IllegalArgumentException("Длительность не должна быть отрицательной");
            }
            copy.put(type, duration);
            total = total.plus(duration);
        }
        this.timeByType = Map.copyOf(copy);
        this.totalDuration = total;
        this.paymentKopecks = paymentKopecks;
    }

    /**
     * Возвращает сотрудника отчёта.
     * @return сотрудник отчёта
     */
    public Employee getEmployee() {
        return employee;
    }

    /**
     * Возвращает месяц отчёта.
     * @return месяц отчёта
     */
    public YearMonth getMonth() {
        return month;
    }

    /**
     * Возвращает часовой пояс отчёта.
     * @return часовой пояс границ месяца
     */
    public ZoneId getZone() {
        return zone;
    }

    /**
     * Возвращает длительности по всем типам времени.
     * @return неизменяемая карта длительностей
     */
    public Map<TimeType, Duration> getTimeByType() {
        return timeByType;
    }

    /**
     * Возвращает общую фактическую длительность работы.
     * @return сумма длительностей всех типов
     */
    public Duration getTotalDuration() {
        return totalDuration;
    }

    /**
     * Возвращает оплату за месяц.
     * @return оплата в копейках
     */
    public long getPaymentKopecks() {
        return paymentKopecks;
    }
}
