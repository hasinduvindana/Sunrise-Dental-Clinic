package service.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Patients aged 60 and over. The consultation fee is waived in full and 15% is
 * taken off treatments, which is the clinic's stated concession policy.
 */
public class SeniorCitizenPricing implements PricingStrategy {

    private static final BigDecimal PERCENT = new BigDecimal("15");

    private final BigDecimal consultationFee;

    public SeniorCitizenPricing(BigDecimal consultationFee) {
        this.consultationFee = consultationFee == null ? BigDecimal.ZERO : consultationFee;
    }

    @Override
    public String name() {
        return "SENIOR";
    }

    @Override
    public String description() {
        return "Senior citizen concession: consultation waived, 15% off treatments";
    }

    @Override
    public BigDecimal calculateDiscount(BigDecimal gross) {
        BigDecimal treatments = gross.subtract(consultationFee).max(BigDecimal.ZERO);
        BigDecimal treatmentDiscount = treatments.multiply(PERCENT)
                                                 .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return consultationFee.add(treatmentDiscount).setScale(2, RoundingMode.HALF_UP);
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
