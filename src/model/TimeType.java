package model;

import java.math.BigDecimal;


/**
 * Типы рабочего времени и соответствующие им коэффициенты оплаты.
 */
public enum TimeType {
    /** Обычное рабочее время. */
    REGULAR("1.0"),
    /** Время, отработанное сверх дневного порога. */
    OVERTIME("1.5"),
    /** Ночное время с 22:00 до 06:00. */
    NIGHT("1.2"),
    /** Время, отработанное в праздничную дату. */
    HOLIDAY("2.0");

    private final BigDecimal coefficient;

    TimeType(String coefficient) {
        this.coefficient = new BigDecimal(coefficient);
    }


    /**
     * Возвращает коэффициент оплаты для типа времени.
     *
     * @return коэффициент оплаты
     */
    public BigDecimal getCoefficient() {
        return coefficient;
    }
}
