package pos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The clinic must never store a full card number. These tests pin that rule
 * down, since the masking runs on every card payment the cashier takes.
 */
class CardMaskingTest {

    @Test
    @DisplayName("only the first four and last four digits survive")
    void masksTheMiddle() {
        assertEquals("4111-XXXX-XXXX-8921", PosCommandDAO.maskCard("4111222233338921"));
    }

    @Test
    @DisplayName("spaces typed by the cashier are ignored")
    void ignoresSpacing() {
        assertEquals("4111-XXXX-XXXX-8921", PosCommandDAO.maskCard("4111 2222 3333 8921"));
    }

    @Test
    @DisplayName("the full number never appears in the stored value")
    void neverLeaksTheNumber() {
        String card = "4111222233338921";
        assertFalse(PosCommandDAO.maskCard(card).contains(card));
        assertFalse(PosCommandDAO.maskCard(card).contains("2222"));
    }

    @Test
    @DisplayName("a cash payment carries no card details at all")
    void noCard() {
        assertNull(PosCommandDAO.maskCard(null));
        assertNull(PosCommandDAO.maskCard(""));
    }
}
