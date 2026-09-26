package model;

import java.util.Objects;


/**
 * Неизменяемые сведения о сотруднике и его почасовой ставке.
 */
public final class Employee {
    private final long id;
    private final String name;
    private final long hourlyRateKopecks;


    /**
     * Создаёт сотрудника.
     *
     * @param id уникальный положительный идентификатор
     * @param name имя сотрудника; окружающие пробелы удаляются
     * @param hourlyRateKopecks почасовая ставка в копейках
     * @throws NullPointerException если {@code name} равен {@code null}
     * @throws IllegalArgumentException если идентификатор или ставка не положительны,
     *                                  либо имя пустое
     */
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

    /**
     * Возвращает уникальный идентификатор сотрудника.
     *
     * @return уникальный идентификатор сотрудника
     */
    public long getId() {
        return id;
    }

    /**
     * Возвращает имя сотрудника.
     *
     * @return имя сотрудника
     */
    public String getName() {
        return name;
    }

    /**
     * Возвращает почасовую ставку.
     *
     * @return почасовая ставка в копейках
     */
    public long getHourlyRateKopecks() {
        return hourlyRateKopecks;
    }
}
