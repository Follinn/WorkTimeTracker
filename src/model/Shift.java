package model;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;


/**
 * Неизменяемая рабочая смена сотрудника с фактическими моментами начала и окончания.
 */
public final class Shift {
    private final Employee employee;
    private final ZonedDateTime start;
    private final ZonedDateTime end;


    /**
     * Создаёт смену.
     *
     * @param employee сотрудник, которому принадлежит смена
     * @param start момент начала смены
     * @param end момент окончания смены
     * @throws NullPointerException если любой аргумент равен {@code null}
     * @throws IllegalArgumentException если окончание не позже начала
     */
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


    /**
     * Возвращает сотрудника смены.
     *
     * @return сотрудник, которому принадлежит смена
     */
    public Employee getEmployee() {
        return employee;
    }


    /**
     * Возвращает момент начала смены.
     *
     * @return момент начала смены
     */
    public ZonedDateTime getStart() {
        return start;
    }


    /**
     * Возвращает момент окончания смены.
     *
     * @return момент окончания смены
     */
    public ZonedDateTime getEnd() {
        return end;
    }


    /**
     * Возвращает фактическую длительность смены с учётом смещения часового пояса.
     *
     * @return интервал между началом и окончанием смены
     */
    public Duration getDuration() {
        return Duration.between(start.toInstant(), end.toInstant());
    }
}
