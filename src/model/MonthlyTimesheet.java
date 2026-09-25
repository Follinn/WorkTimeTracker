package model;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public final class MonthlyTimesheet {

    private final YearMonth month;
    private final ZoneId zone;
    private final List<Shift> shifts = new ArrayList<>();


    public MonthlyTimesheet(YearMonth month, ZoneId zone) {
        this.month = Objects.requireNonNull(
                month, "Месяц не должен быть null"
        );
        this.zone = Objects.requireNonNull(
                zone, "Часовой пояс не должен быть null"
        );
    }


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


    public List<Shift> getShifts() {
        return List.copyOf(shifts);
    }


    public List<Shift> getShiftsByEmployee(long employeeId) {
        List<Shift> result = new ArrayList<>();

        for (Shift shift : shifts) {
            if (shift.getEmployee().getId() == employeeId) {
                result.add(shift);
            }
        }

        return List.copyOf(result);
    }


    public YearMonth getMonth() {
        return month;
    }

    public ZoneId getZone() {
        return zone;
    }
}