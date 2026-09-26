package model;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;


/**
 * Неизменяемый отрезок рабочего времени, отнесённый к одному типу оплаты.
 */
public final class TimeSegment {

    private final ZonedDateTime start;
    private final ZonedDateTime end;
    private final TimeType type;


    /**
     * Создаёт отрезок рабочего времени.
     *
     * @param start момент начала отрезка
     * @param end момент окончания отрезка
     * @param type тип учитываемого времени
     * @throws NullPointerException если любой аргумент равен {@code null}
     * @throws IllegalArgumentException если окончание не позже начала
     */
    public TimeSegment(
            ZonedDateTime start,
            ZonedDateTime end,
            TimeType type
    ) {
        this.start = Objects.requireNonNull(
                start, "Начало отрезка не должно быть null"
        );
        this.end = Objects.requireNonNull(
                end, "Конец отрезка не должен быть null"
        );
        this.type = Objects.requireNonNull(
                type, "Тип рабочего времени не должен быть null"
        );

        if (!end.toInstant().isAfter(start.toInstant())) {
            throw new IllegalArgumentException(
                    "Конец отрезка должен быть позже начала"
            );
        }
    }


    /**
     * Возвращает момент начала отрезка.
     *
     * @return момент начала отрезка
     */
    public ZonedDateTime getStart() {
        return start;
    }


    /**
     * Возвращает момент окончания отрезка.
     *
     * @return момент окончания отрезка
     */
    public ZonedDateTime getEnd() {
        return end;
    }


    /**
     * Возвращает тип рабочего времени отрезка.
     *
     * @return тип рабочего времени отрезка
     */
    public TimeType getType() {
        return type;
    }

    /**
     * Вычисляет фактическую длительность с учётом смещений часового пояса.
     *
     * @return интервал между началом и окончанием отрезка
     */
    public Duration getDuration() {
        return Duration.between(start.toInstant(), end.toInstant());
    }
}
