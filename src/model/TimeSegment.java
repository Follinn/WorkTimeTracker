package model;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;


public final class TimeSegment {

    private final ZonedDateTime start;
    private final ZonedDateTime end;
    private final TimeType type;


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


    public ZonedDateTime getStart() {
        return start;
    }


    public ZonedDateTime getEnd() {
        return end;
    }


    public TimeType getType() {
        return type;
    }

    //Вычисляет фактическую длительность с учётом смещений часового пояса.
    public Duration getDuration() {
        return Duration.between(start.toInstant(), end.toInstant());
    }
}