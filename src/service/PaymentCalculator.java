package service;

import model.Employee;
import model.TimeType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Рассчитывает оплату с единственным округлением общей суммы до копеек.
 */
public final class PaymentCalculator {
    private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

    /**
     * Создаёт калькулятор оплаты.
     */
    public PaymentCalculator() {
    }

    /**
     * Рассчитывает оплату по ставке сотрудника и длительностям разных типов.
     * Отсутствующие типы считаются нулевыми. Секунды и наносекунды учитываются
     * точно; округление {@link RoundingMode#HALF_UP} выполняется после суммирования.
     *
     * @param employee сотрудник, чья ставка применяется ко всему расчёту
     * @param timeByType длительности по типам рабочего времени
     * @return общая оплата в копейках
     * @throws NullPointerException если аргумент, ключ или значение карты равен {@code null}
     * @throws IllegalArgumentException если длительность отрицательная
     * @throws ArithmeticException если округлённая оплата не помещается в {@code long}
     */
    public long calculate(Employee employee, Map<TimeType, Duration> timeByType) {
        Objects.requireNonNull(employee, "Сотрудник не должен быть null");
        Objects.requireNonNull(timeByType, "Карта длительностей не должна быть null");
        BigDecimal rate = BigDecimal.valueOf(employee.getHourlyRateKopecks());
        BigDecimal weightedSeconds = BigDecimal.ZERO;

        for (Map.Entry<TimeType, Duration> entry : timeByType.entrySet()) {
            TimeType type = Objects.requireNonNull(entry.getKey(), "Тип времени не должен быть null");
            Duration duration = Objects.requireNonNull(entry.getValue(), "Длительность не должна быть null");
            if (duration.isNegative()) {
                throw new IllegalArgumentException("Длительность не должна быть отрицательной");
            }
            BigDecimal seconds = BigDecimal.valueOf(duration.getSeconds())
                    .add(BigDecimal.valueOf(duration.getNano(), 9));
            weightedSeconds = weightedSeconds.add(seconds.multiply(type.getCoefficient()));
        }

        // Деление на 3600 откладываем до конца, чтобы не округлять отдельные части.
        BigDecimal payment = weightedSeconds.multiply(rate)
                .divide(SECONDS_PER_HOUR, 0, RoundingMode.HALF_UP);
        try {
            return payment.longValueExact();
        } catch (ArithmeticException exception) {
            throw new ArithmeticException("Оплата в копейках превышает диапазон long");
        }
    }
}
