package service;

import model.Employee;
import model.TimeType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static model.TimeType.*;
import static org.junit.jupiter.api.Assertions.*;

class PaymentCalculatorTest {
    private final PaymentCalculator calculator = new PaymentCalculator();
    private final Employee employee = new Employee(1, "Анна", 10_000);

    @Test
    void paysAllTypesWithTheirCoefficients() {
        assertEquals(57_000, calculator.calculate(employee, Map.of(
                REGULAR, Duration.ofHours(1), OVERTIME, Duration.ofHours(1),
                NIGHT, Duration.ofHours(1), HOLIDAY, Duration.ofHours(1))));
    }

    @Test
    void emptyAndZeroDurationsProduceZeroPayment() {
        assertEquals(0, calculator.calculate(employee, Map.of()));
        assertEquals(0, calculator.calculate(employee, Map.of(REGULAR, Duration.ZERO)));
    }

    @Test
    void roundsOnceAfterAddingDifferentTypes() {
        Employee lowRate = new Employee(1, "Анна", 1);
        // 0,25 + 0,30 = 0,55 копейки: отдельное округление ошибочно дало бы ноль.
        assertEquals(1, calculator.calculate(lowRate, Map.of(
                REGULAR, Duration.ofMinutes(15), NIGHT, Duration.ofMinutes(15))));
    }

    @Test
    void halfUpRoundsExactHalfUpAndSmallerFractionDown() {
        Employee lowRate = new Employee(1, "Анна", 1);
        assertEquals(1, calculator.calculate(lowRate, Map.of(REGULAR, Duration.ofMinutes(30))));
        assertEquals(0, calculator.calculate(lowRate,
                Map.of(REGULAR, Duration.ofMinutes(30).minusNanos(1))));
    }

    @Test
    void preservesNanoseconds() {
        Employee highRate = new Employee(1, "Анна", 1_800_000_000_000L);
        assertEquals(1, calculator.calculate(highRate, Map.of(REGULAR, Duration.ofNanos(1))));
        assertEquals(2, calculator.calculate(highRate, Map.of(REGULAR, Duration.ofNanos(3))));
    }

    @Test
    void preservesLongPrecisionAndRejectsOverflow() {
        Employee highRate = new Employee(1, "Анна", Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, calculator.calculate(highRate, Map.of(REGULAR, Duration.ofHours(1))));
        assertThrows(ArithmeticException.class,
                () -> calculator.calculate(highRate, Map.of(REGULAR, Duration.ofHours(2))));
    }

    @Test
    void longDurationDoesNotRequireConversionToLongNanoseconds() {
        Employee lowRate = new Employee(1, "Анна", 1);
        assertEquals(10_000_000, calculator.calculate(lowRate, Map.of(REGULAR, Duration.ofHours(10_000_000))));
    }

    @Test
    void rejectsNullsAndNegativeDurations() {
        assertThrows(NullPointerException.class, () -> calculator.calculate(null, Map.of()));
        assertThrows(NullPointerException.class, () -> calculator.calculate(employee, null));
        Map<TimeType, Duration> invalid = new HashMap<>();
        invalid.put(null, Duration.ZERO);
        assertThrows(NullPointerException.class, () -> calculator.calculate(employee, invalid));
        invalid.clear();
        invalid.put(REGULAR, null);
        assertThrows(NullPointerException.class, () -> calculator.calculate(employee, invalid));
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(employee, Map.of(REGULAR, Duration.ofNanos(-1))));
    }
}
