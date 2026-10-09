package service;

import model.Shift;
import model.TimeSegment;
import model.TimeType;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Подсчитывает фактически отработанное время одного сотрудника за период.
 * Начало периода включается, конец исключается. Типы определяются по полным
 * сменам, поэтому часы до начала периода могут влиять на сверхурочные.
 */
public final class TimeCalculator {
    private final ShiftSplitter splitter;

    /**
     * Создаёт калькулятор с заданными правилами разбиения смен.
     *
     * @param splitter обработчик с часовым поясом табеля, праздниками и дневным порогом
     * @throws NullPointerException если обработчик равен {@code null}
     */
    public TimeCalculator(ShiftSplitter splitter) {
        this.splitter = Objects.requireNonNull(
                splitter, "Обработчик смен не должен быть null"
        );
    }

    /**
     * Возвращает общую длительность работы в периоде без учёта перерывов.
     * Для пустого периода или списка смен возвращает нулевую длительность.
     *
     * @param employeeShifts полные смены одного сотрудника, включая работу
     *                       до начала периода в те же местные сутки
     * @param periodStart начало периода включительно
     * @param periodEnd конец периода исключительно; может совпадать с началом
     * @return сумма фактических длительностей пересечений с периодом
     * @throws NullPointerException если аргумент или элемент списка равен {@code null}
     * @throws IllegalArgumentException если конец периода раньше начала,
     *                                  смены пересекаются или относятся к разным сотрудникам
     */
    public Duration calculateTotal(
            List<Shift> employeeShifts,
            Instant periodStart,
            Instant periodEnd
    ) {
        Map<TimeType, Duration> byType = calculateByType(employeeShifts, periodStart, periodEnd);
        Duration total = Duration.ZERO;
        for (Duration duration : byType.values()) {
            total = total.plus(duration);
        }
        return total;
    }

    /**
     * Возвращает длительность работы по каждому типу в заданном периоде.
     * Сначала разбивает полные смены, затем учитывает только пересечения
     * полученных отрезков с периодом. Отсутствующие типы имеют нулевую длительность.
     *
     * @param employeeShifts полные смены одного сотрудника, включая работу
     *                       до начала периода в те же местные сутки
     * @param periodStart начало периода включительно
     * @param periodEnd конец периода исключительно; может совпадать с началом
     * @return неизменяемая карта длительностей для всех значений {@link TimeType}
     * @throws NullPointerException если аргумент или элемент списка равен {@code null}
     * @throws IllegalArgumentException если конец периода раньше начала,
     *                                  смены пересекаются или относятся к разным сотрудникам
     */
    public Map<TimeType, Duration> calculateByType(
            List<Shift> employeeShifts,
            Instant periodStart,
            Instant periodEnd
    ) {
        Objects.requireNonNull(employeeShifts, "Список смен не должен быть null");
        Objects.requireNonNull(periodStart, "Начало периода не должно быть null");
        Objects.requireNonNull(periodEnd, "Конец периода не должен быть null");
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("Конец периода не должен быть раньше начала");
        }

        Map<TimeType, Duration> result = new EnumMap<>(TimeType.class);
        for (TimeType type : TimeType.values()) {
            result.put(type, Duration.ZERO);
        }

        // Не обрезаем смены заранее: это сбросило бы накопленные за сутки часы.
        for (TimeSegment segment : splitter.split(employeeShifts)) {
            Instant start = segment.getStart().toInstant();
            Instant end = segment.getEnd().toInstant();
            if (start.isBefore(periodStart)) {
                start = periodStart;
            }
            if (end.isAfter(periodEnd)) {
                end = periodEnd;
            }
            if (start.isBefore(end)) {
                Duration intersection = Duration.between(start, end);
                TimeType type = segment.getType();
                result.put(type, result.get(type).plus(intersection));
            }
        }
        return Map.copyOf(result);
    }
}
