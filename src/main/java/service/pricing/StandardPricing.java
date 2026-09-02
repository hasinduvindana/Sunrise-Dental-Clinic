package service.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Ordinary walk-in patient: no discount. */
public class StandardPricing implements PricingStrategy {

    @Override
    public String name() {
        return "STANDARD";
    }

    @Override
    public String description() {
        return "Standard rate";
    }

    @Override
    public BigDecimal calculateDiscount(BigDecimal gross) {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public BigDecimal calculateTax(BigDecimal netAmount, BigDecimal taxPercent) {
        if (taxPercent == null || taxPercent.signum() == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return netAmount.multiply(taxPercent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
