package model;

import java.math.BigDecimal;


public enum TimeType {
    REGULAR("1.0"),
    OVERTIME("1.5"),
    NIGHT("1.2"),
    HOLIDAY("2.0");

    private final BigDecimal coefficient;

    TimeType(String coefficient) {
        this.coefficient = new BigDecimal(coefficient);
    }


    public BigDecimal getCoefficient() {
        return coefficient;
    }
}