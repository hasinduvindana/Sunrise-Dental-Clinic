package service.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Registered VIP patient. The percentage is not hard-coded: it comes from the
 * billing.vip.discount.percent setting, so an admin can change it from the
 * settings screen without a redeploy.
 */
public class VipPricing implements PricingStrategy {

    private final BigDecimal discountPercent;

    public VipPricing(BigDecimal discountPercent) {
        this.discountPercent = discountPercent == null ? BigDecimal.TEN : discountPercent;
    }

    @Override
    public String name() {
        return "VIP";
    }

    @Override
    public String description() {
        return "VIP member discount " + discountPercent.stripTrailingZeros().toPlainString() + "%";
    }

    @Override
    public BigDecimal calculateDiscount(BigDecimal gross) {
        return gross.multiply(discountPercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
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
