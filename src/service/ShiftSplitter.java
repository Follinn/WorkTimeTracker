package service;

import model.Shift;
import model.TimeSegment;
import model.TimeType;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Разбивает смены сотрудника на отрезки разных типов времени.
 * Местные даты и ночные границы определяются в заданном часовом поясе.
 * Дневной порог учитывает фактическую длительность всех смен за местные сутки,
 * включая ночные и праздничные часы, но не перерывы между сменами.
 */
public final class ShiftSplitter {

    private final ZoneId zone;
    private final Set<LocalDate> holidays;
    private final Duration dailyThreshold;

    /**
     * Создаёт обработчик смен.
     *
     * @param zone часовой пояс расчёта
     * @param holidays праздничные даты
     * @param dailyThreshold дневной порог обычного времени
     * @throws NullPointerException если аргумент или элемент множества праздников равен {@code null}
     * @throws IllegalArgumentException если порог не положительный
     */
    public ShiftSplitter(
            ZoneId zone,
            Set<LocalDate> holidays,
            Duration dailyThreshold
    ) {
        this.zone = Objects.requireNonNull(
                zone, "Часовой пояс не должен быть null"
        );
        Objects.requireNonNull(holidays, "Праздники не должны быть null");
        for (LocalDate holiday : holidays) {
            Objects.requireNonNull(holiday, "Дата праздника не должна быть null");
        }
        this.holidays = Set.copyOf(holidays);
        this.dailyThreshold = Objects.requireNonNull(
                dailyThreshold, "Дневной порог не должен быть null"
        );

        if (dailyThreshold.isNegative() || dailyThreshold.isZero()) {
            throw new IllegalArgumentException(
                    "Дневной порог должен быть положительным"
            );
        }
    }

    /**
     * Разбивает смены одного сотрудника на отрезки с единым типом времени.
     * Приоритет типов: праздничное, сверхурочное, ночное, обычное.
     * Смены сортируются по моменту начала без изменения исходного списка.
     * Сотрудник определяется по ID; соприкасающиеся смены допустимы.
     * При каждом вызове накопленное время рассчитывается заново.
     * Соседние отрезки могут иметь одинаковый тип: границы смен, суток
     * и переводов часов сохраняются.
     *
     * @param employeeShifts смены одного сотрудника
     * @return неизменяемый список отрезков в хронологическом порядке
     * @throws NullPointerException если список или один из его элементов равен {@code null}
     * @throws IllegalArgumentException если смены принадлежат разным сотрудникам
     *                                  или пересекаются
     */
    public List<TimeSegment> split(List<Shift> employeeShifts) {
        Objects.requireNonNull(
                employeeShifts, "Список смен не должен быть null"
        );

        List<Shift> sortedShifts = new ArrayList<>(employeeShifts);

        for (Shift shift : sortedShifts) {
            Objects.requireNonNull(
                    shift, "Список не должен содержать null"
            );
        }

        sortedShifts.sort(
                Comparator.comparing(shift -> shift.getStart().toInstant())
        );

        if (sortedShifts.isEmpty()) {
            return List.of();
        }

        long employeeId = sortedShifts.get(0).getEmployee().getId();

        for (int i = 0; i < sortedShifts.size(); i++) {
            Shift current = sortedShifts.get(i);

            if (current.getEmployee().getId() != employeeId) {
                throw new IllegalArgumentException(
                        "Все смены должны принадлежать одному сотруднику"
                );
            }

            if (i > 0) {
                Shift previous = sortedShifts.get(i - 1);

                if (current.getStart().toInstant()
                        .isBefore(previous.getEnd().toInstant())) {
                    throw new IllegalArgumentException(
                            "Смены сотрудника не должны пересекаться"
                    );
                }
            }
        }

        List<TimeSegment> result = new ArrayList<>();

        // Храним отработанное время отдельно для каждой даты.
        Map<LocalDate, Duration> workedByDate = new HashMap<>();

        for (Shift shift : sortedShifts) {
            ZonedDateTime current = shift.getStart().withZoneSameInstant(zone);
            ZonedDateTime shiftEnd = shift.getEnd().withZoneSameInstant(zone);

            while (current.isBefore(shiftEnd)) {
                LocalDate date = current.toLocalDate();
                LocalTime time = current.toLocalTime();

                Duration worked = workedByDate.getOrDefault(
                        date, Duration.ZERO
                );

                // Определяем тип текущего отрезка.
                TimeType type;

                if (holidays.contains(date)) {
                    type = TimeType.HOLIDAY;
                } else if (worked.compareTo(dailyThreshold) >= 0) {
                    type = TimeType.OVERTIME;
                } else if (time.isBefore(LocalTime.of(6, 0))
                        || !time.isBefore(LocalTime.of(22, 0))) {
                    type = TimeType.NIGHT;
                } else {
                    type = TimeType.REGULAR;
                }

                // Изначально считаем, что отрезок длится до конца смены.
                ZonedDateTime segmentEnd = shiftEnd;

                ZonedDateTime midnight = date.plusDays(1).atStartOfDay(zone);
                ZonedDateTime morning = date.atTime(6, 0).atZone(zone);
                ZonedDateTime night = date.atTime(22, 0).atZone(zone);

                // Выбираем ближайшую границу впереди текущего момента.
                for (ZonedDateTime boundary : List.of(midnight, morning, night)) {
                    if (boundary.isAfter(current)
                            && boundary.isBefore(segmentEnd)) {
                        segmentEnd = boundary;
                    }
                }

                // При переводе часов заново определяем местную дату и время.
                var transition = zone.getRules()
                        .nextTransition(current.toInstant());

                if (transition != null
                        && transition.getInstant().isBefore(segmentEnd.toInstant())) {
                    segmentEnd = transition.getInstant().atZone(zone);
                }

                // Если порог ещё не достигнут, он тоже может стать границей.
                if (worked.compareTo(dailyThreshold) < 0) {
                    Duration remaining = dailyThreshold.minus(worked);
                    Duration segmentDuration = Duration.between(
                            current.toInstant(), segmentEnd.toInstant()
                    );

                    // Вычисляем дату порога только внутри текущего отрезка.
                    // Это также исключает переполнение при очень большом пороге.
                    if (remaining.compareTo(segmentDuration) < 0) {
                        segmentEnd = current.toInstant().plus(remaining).atZone(zone);
                    }
                }

                TimeSegment segment = new TimeSegment(
                        current, segmentEnd, type
                );

                result.add(segment);

                workedByDate.put(
                        date, worked.plus(segment.getDuration())
                );

                current = segmentEnd;
            }
        }

        return List.copyOf(result);
    }
}
