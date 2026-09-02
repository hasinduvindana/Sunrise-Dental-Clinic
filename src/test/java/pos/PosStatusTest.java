package pos;

import exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The clinic's wording and the database's wording for a patient's progress
 * must stay reversible, or a nurse's confirmation would come back as
 * something else on the doctor's queue.
 */
class PosStatusTest {

    @Test
    @DisplayName("database wording becomes the clinic's wording")
    void toPos() {
        assertEquals("SCHEDULED", PosStatus.toPos("BOOKED"));
        assertEquals("CONFIRMED_BY_NURSE", PosStatus.toPos("CHECKED_IN"));
        assertEquals("TREATMENT_COMPLETED", PosStatus.toPos("COMPLETED"));
        assertEquals("PAID", PosStatus.toPos("PAID"));
        assertEquals("CANCELLED", PosStatus.toPos("CANCELLED"));
    }

    @Test
    @DisplayName("every clinic status converts back to the database value")
    void roundTrip() {
        String[] dbValues = {"BOOKED", "PAID", "CHECKED_IN", "IN_CONSULTATION", "COMPLETED", "CANCELLED"};
        for (String db : dbValues) {
            assertEquals(db, PosStatus.toDb(PosStatus.toPos(db)), db + " did not survive the round trip");
        }
    }

    @Test
    @DisplayName("a missing status defaults to a new booking rather than failing")
    void nullIsSafe() {
        assertEquals("SCHEDULED", PosStatus.toPos(null));
        assertEquals("BOOKED", PosStatus.toDb(null));
    }

    @Test
    @DisplayName("an invented status is refused instead of being written to the database")
    void unknownStatus() {
        assertThrows(ValidationException.class, () -> PosStatus.toDb("HAVING_LUNCH"));
    }
}
