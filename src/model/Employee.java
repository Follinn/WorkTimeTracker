package model;

import java.util.Objects;


public final class Employee {
    private final long id;
    private final String name;
    private final long hourlyRateKopecks;


    public Employee(long id, String name, long hourlyRateKopecks) {
        if (id <= 0) {
            throw new IllegalArgumentException("Идентификатор сотрудника должен быть положительным");
        }
        this.name = Objects.requireNonNull(name, "Имя сотрудника не должно быть null").trim();
        if (this.name.isEmpty()) {
            throw new IllegalArgumentException("Имя сотрудника не должно состоять только из пробелов");
        }
        if (hourlyRateKopecks <= 0) {
            throw new IllegalArgumentException("Почасовая ставка должна быть положительной");
        }
        this.id = id;
        this.hourlyRateKopecks = hourlyRateKopecks;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public long getHourlyRateKopecks() {
        return hourlyRateKopecks;
    }
}
