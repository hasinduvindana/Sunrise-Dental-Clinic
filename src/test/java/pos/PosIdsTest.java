package pos;

import exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The POS screens and the database identify the same record differently.
 * These tests fix the rule for converting between the two, because an id that
 * survives the round trip is what keeps a booking attached to the right
 * patient once it comes back from the browser.
 */
class PosIdsTest {

    @Test
    @DisplayName("display ids are formatted the way the screens expect")
    void formatting() {
        assertEquals("USR-003", PosIds.user(3));
        assertEquals("USR-042", PosIds.user(42));
        assertEquals("TRT-007", PosIds.treatment(7));
        assertEquals("SRD-APT-0012", PosIds.appointment(12));
        assertEquals("REP-0005", PosIds.report(5));
        assertEquals("SES-2026-004", PosIds.session(4, "2026-09-02"));
    }

    @Test
    @DisplayName("every display id converts back to its database key")
    void roundTrip() {
        assertEquals(3, PosIds.numeric(PosIds.user(3), "User"));
        assertEquals(12, PosIds.numeric(PosIds.appointment(12), "Appointment"));
        assertEquals(4, PosIds.numeric(PosIds.session(4, "2026-09-02"), "Session"));
        assertEquals(5, PosIds.numeric(PosIds.report(5), "Report"));
    }

    @Test
    @DisplayName("a plain number is accepted as well as a prefixed id")
    void plainNumber() {
        assertEquals(9, PosIds.numeric("9", "User"));
    }

    @Test
    @DisplayName("a missing or malformed id is refused with a readable message")
    void rejectsRubbish() {
        assertThrows(ValidationException.class, () -> PosIds.numeric(null, "Session"));
        assertThrows(ValidationException.class, () -> PosIds.numeric("  ", "Session"));
        ValidationException e = assertThrows(ValidationException.class,
                () -> PosIds.numeric("SES-2026-abc", "Session"));
        assertEquals(true, e.getMessage().contains("Session"));
    }

    @Test
    @DisplayName("optional ids return null instead of failing")
    void optional() {
        assertNull(PosIds.optionalNumeric(null));
        assertNull(PosIds.optionalNumeric(""));
        assertNull(PosIds.optionalNumeric("TRT-xyz"));
        assertEquals(7, PosIds.optionalNumeric("TRT-007"));
    }

    @Test
    @DisplayName("a session id carries the year of the session, not of today")
    void sessionYearComesFromTheDate() {
        assertEquals("SES-2025-011", PosIds.session(11, "2025-12-31"));
    }
}
