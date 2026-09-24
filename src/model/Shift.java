package model;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;


public final class Shift {
    private final Employee employee;
    private final ZonedDateTime start;
    private final ZonedDateTime end;


    public Shift(Employee employee, ZonedDateTime start, ZonedDateTime end) {
        this.employee = Objects.requireNonNull(
                employee, "Сотрудник не должен быть null"
        );
        this.start = Objects.requireNonNull(
                start, "Начало смены не должно быть null"
        );
        this.end = Objects.requireNonNull(
                end, "Окончание смены не должно быть null"
        );

        if (!this.end.toInstant().isAfter(this.start.toInstant())) {
            throw new IllegalArgumentException(
                    "Окончание смены должно быть позже её начала"
            );
        }
    }


    public Employee getEmployee() {
        return employee;
    }


    public ZonedDateTime getStart() {
        return start;
    }


    public ZonedDateTime getEnd() {
        return end;
    }


    public Duration getDuration() {
        return Duration.between(start.toInstant(), end.toInstant());
    }
}