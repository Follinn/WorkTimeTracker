package model;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Табель смен за месяц в заданном часовом поясе.
 * Не допускает пересекающихся смен одного сотрудника.
 */
public final class MonthlyTimesheet {

    private final YearMonth month;
    private final ZoneId zone;
    private final List<Shift> shifts = new ArrayList<>();


    /**
     * Создаёт пустой табель за месяц.
     *
     * @param month учитываемый месяц
     * @param zone часовой пояс границ месяца
     * @throws NullPointerException если любой аргумент равен {@code null}
     */
    public MonthlyTimesheet(YearMonth month, ZoneId zone) {
        this.month = Objects.requireNonNull(
                month, "Месяц не должен быть null"
        );
        this.zone = Objects.requireNonNull(
                zone, "Часовой пояс не должен быть null"
        );
    }


    /**
     * Добавляет смену, пересекающуюся с указанным месяцем.
     *
     * @param shift добавляемая смена
     * @throws NullPointerException если {@code shift} равен {@code null}
     * @throws IllegalArgumentException если смена не относится к месяцу или
     *                                  пересекается со сменой того же сотрудника
     */
    public void addShift(Shift shift) {
        Objects.requireNonNull(shift, "Смена не должна быть null");

        Instant monthStart = month.atDay(1)
                .atStartOfDay(zone)
                .toInstant();

        Instant monthEnd = month.plusMonths(1)
                .atDay(1)
                .atStartOfDay(zone)
                .toInstant();

        Instant shiftStart = shift.getStart().toInstant();
        Instant shiftEnd = shift.getEnd().toInstant();

        if (!shiftStart.isBefore(monthEnd)
                || !shiftEnd.isAfter(monthStart)) {
            throw new IllegalArgumentException(
                    "Смена не попадает в месяц табеля: " + month
            );
        }

        for (Shift existing : shifts) {
            if (existing.getEmployee().getId()
                    != shift.getEmployee().getId()) {
                continue;
            }

            Instant existingStart = existing.getStart().toInstant();
            Instant existingEnd = existing.getEnd().toInstant();

            if (shiftStart.isBefore(existingEnd)
                    && shiftEnd.isAfter(existingStart)) {
                throw new IllegalArgumentException(
                        "Смена пересекается с другой сменой сотрудника "
                                + shift.getEmployee().getId()
                );
            }
        }

        shifts.add(shift);
    }


    /**
     * Возвращает все смены табеля.
     *
     * @return неизменяемый снимок списка смен
     */
    public List<Shift> getShifts() {
        return List.copyOf(shifts);
    }


    /**
     * Возвращает смены сотрудника с указанным идентификатором.
     *
     * @param employeeId идентификатор сотрудника
     * @return неизменяемый список найденных смен
     */
    public List<Shift> getShiftsByEmployee(long employeeId) {
        List<Shift> result = new ArrayList<>();

        for (Shift shift : shifts) {
            if (shift.getEmployee().getId() == employeeId) {
                result.add(shift);
            }
        }

        return List.copyOf(result);
    }


    /**
     * Возвращает месяц табеля.
     *
     * @return месяц, к которому относится табель
     */
    public YearMonth getMonth() {
        return month;
    }

    /**
     * Возвращает часовой пояс границ месяца.
     *
     * @return часовой пояс границ месяца
     */
    public ZoneId getZone() {
        return zone;
    }
}
