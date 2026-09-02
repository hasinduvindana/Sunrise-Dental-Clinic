package service;

import model.Patient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import service.pricing.PricingStrategy;
import service.pricing.PricingStrategyFactory;
import service.pricing.SeniorCitizenPricing;
import service.pricing.StandardPricing;
import service.pricing.VipPricing;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The billing rules were specified as these tests first, then the Strategy
 * classes were written until each one passed. Money is compared with
 * compareTo through assertEquals on the string form so scale does not matter.
 */
class PricingStrategyTest {

    private static final BigDecimal VIP_PERCENT = new BigDecimal("10");

    @Test
    @DisplayName("a walk-in patient gets no discount")
    void standardHasNoDiscount() {
        PricingStrategy strategy = new StandardPricing();
        assertEquals("0.00", strategy.calculateDiscount(new BigDecimal("10000.00")).toPlainString());
    }

    @Test
    @DisplayName("a VIP patient gets the configured percentage off the gross")
    void vipDiscount() {
        PricingStrategy strategy = new VipPricing(VIP_PERCENT);
        assertEquals("1000.00", strategy.calculateDiscount(new BigDecimal("10000.00")).toPlainString());
    }

    @Test
    @DisplayName("a senior citizen has the consultation waived and 15% off treatments")
    void seniorDiscount() {
        // 1500 consultation + 10000 treatments -> 1500 + 1500 = 3000 off
        PricingStrategy strategy = new SeniorCitizenPricing(new BigDecimal("1500.00"));
        assertEquals("3000.00", strategy.calculateDiscount(new BigDecimal("11500.00")).toPlainString());
    }

    @Test
    @DisplayName("tax is charged on the amount left after the discount")
    void taxAppliesToNet() {
        PricingStrategy strategy = new StandardPricing();
        assertEquals("450.00", strategy.calculateTax(new BigDecimal("9000.00"), new BigDecimal("5")).toPlainString());
    }

    @Test
    @DisplayName("a zero tax rate produces no tax line")
    void zeroTax() {
        PricingStrategy strategy = new VipPricing(VIP_PERCENT);
        assertEquals("0.00", strategy.calculateTax(new BigDecimal("9000.00"), BigDecimal.ZERO).toPlainString());
    }

    @Test
    @DisplayName("the factory picks VIP pricing for a VIP patient")
    void factoryChoosesVip() {
        Patient patient = new Patient();
        patient.setVip(true);
        PricingStrategy strategy = PricingStrategyFactory.forPatient(patient, VIP_PERCENT, new BigDecimal("1500"));
        assertInstanceOf(VipPricing.class, strategy);
        assertEquals("VIP", strategy.name());
    }

    @Test
    @DisplayName("the factory picks senior pricing for a patient aged 60 or over")
    void factoryChoosesSenior() {
        Patient patient = new Patient();
        patient.setVip(false);
        patient.setDateOfBirth(LocalDate.now().minusYears(65).toString());
        assertInstanceOf(SeniorCitizenPricing.class,
                PricingStrategyFactory.forPatient(patient, VIP_PERCENT, new BigDecimal("1500")));
    }

    @Test
    @DisplayName("a patient with no date of birth falls back to standard pricing")
    void factoryFallsBackToStandard() {
        Patient patient = new Patient();
        assertInstanceOf(StandardPricing.class,
                PricingStrategyFactory.forPatient(patient, VIP_PERCENT, new BigDecimal("1500")));
    }

    @Test
    @DisplayName("an unknown strategy name never fails the bill, it falls back to standard")
    void unknownNameFallsBack() {
        assertInstanceOf(StandardPricing.class,
                PricingStrategyFactory.byName("PLATINUM", VIP_PERCENT, new BigDecimal("1500")));
    }
}
