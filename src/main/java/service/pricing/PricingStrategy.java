package service.pricing;

import java.math.BigDecimal;

/**
 * DESIGN PATTERN: Strategy.
 *
 * How a bill total is worked out changes with the kind of patient (walk-in,
 * VIP member, senior citizen). Each rule is its own class implementing this
 * interface, so adding a new discount scheme means adding a class rather than
 * editing an if/else ladder inside BillingService.
 */
public interface PricingStrategy {

    /** Machine name stored on the bill so a re-print shows the rule that was used. */
    String name();

    /** Human wording printed on the receipt. */
    String description();

    /** Discount taken off the gross amount (consultation + treatments). */
    BigDecimal calculateDiscount(BigDecimal gross);

    /** Tax charged on the discounted amount. */
    BigDecimal calculateTax(BigDecimal netAmount, BigDecimal taxPercent);
}
