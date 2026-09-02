package service.pricing;

import model.Patient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

/**
 * DESIGN PATTERN: Factory Method.
 *
 * Picks the pricing rule that applies to a patient. BillingService asks for a
 * strategy and never needs to know which concrete class it receives.
 */
public final class PricingStrategyFactory {

    private PricingStrategyFactory() { }

    public static PricingStrategy forPatient(Patient patient,
                                             BigDecimal vipDiscountPercent,
                                             BigDecimal consultationFee) {
        if (patient != null && patient.isVip()) {
            return new VipPricing(vipDiscountPercent);
        }
        if (patient != null && isSenior(patient.getDateOfBirth())) {
            return new SeniorCitizenPricing(consultationFee);
        }
        return new StandardPricing();
    }

    /** Explicit override, used when a cashier applies a scheme by hand. */
    public static PricingStrategy byName(String name,
                                         BigDecimal vipDiscountPercent,
                                         BigDecimal consultationFee) {
        if (name == null) {
            return new StandardPricing();
        }
        switch (name.trim().toUpperCase()) {
            case "VIP":    return new VipPricing(vipDiscountPercent);
            case "SENIOR": return new SeniorCitizenPricing(consultationFee);
            default:       return new StandardPricing();
        }
    }

    private static boolean isSenior(String dateOfBirth) {
        if (dateOfBirth == null || dateOfBirth.isBlank() || "null".equals(dateOfBirth)) {
            return false;
        }
        try {
            return Period.between(LocalDate.parse(dateOfBirth), LocalDate.now()).getYears() >= 60;
        } catch (Exception e) {
            return false;
        }
    }
}
