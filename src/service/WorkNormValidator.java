package service;

import exception.WorkNormExceededException;
import model.Shift;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Проверяет норму фактической работы за период независимо от формирования отчёта.
 * Норма периода задаётся отдельно от дневного порога сверхурочного времени.
 */
public final class WorkNormValidator {
    private final TimeCalculator calculator;

    /**
     * Создаёт проверку нормы с заданным калькулятором времени.
     *
     * @param calculator калькулятор фактической длительности работы
     * @throws NullPointerException если калькулятор равен {@code null}
     */
    public WorkNormValidator(TimeCalculator calculator) {
        this.calculator = Objects.requireNonNull(calculator, "Калькулятор времени не должен быть null");
    }

    /**
     * Проверяет, что работа в периоде не превышает заданную норму.
     * Учитывает все типы времени без коэффициентов оплаты. Равенство норме
     * допустимо, нулевая норма разрешена. Данные смен не изменяются.
     *
     * @param employeeShifts полные смены одного сотрудника
     * @param periodStart начало периода включительно
     * @param periodEnd конец периода исключительно; может совпадать с началом
     * @param norm максимально допустимая фактическая длительность за период
     * @throws NullPointerException если аргумент или элемент списка равен {@code null}
     * @throws IllegalArgumentException если норма отрицательная, конец периода
     *                                  раньше начала, смены пересекаются или относятся к разным сотрудникам
     * @throws WorkNormExceededException если фактическая длительность строго больше нормы
     */
    public void validate(List<Shift> employeeShifts, Instant periodStart, Instant periodEnd, Duration norm) {
        Objects.requireNonNull(norm, "Норма рабочего времени не должна быть null");
        if (norm.isNegative()) {
            throw new IllegalArgumentException("Норма рабочего времени не должна быть отрицательной");
        }
        Duration worked = calculator.calculateTotal(employeeShifts, periodStart, periodEnd);
        if (worked.compareTo(norm) > 0) {
            throw new WorkNormExceededException(
                    "Превышена норма рабочего времени за период: отработано " + worked + ", норма " + norm
            );
        }
    }
}
