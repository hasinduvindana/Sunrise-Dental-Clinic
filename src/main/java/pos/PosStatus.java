package pos;

/**
 * The POS screens use the clinic's own wording for where a patient has got to
 * ("CONFIRMED_BY_NURSE"), while the database column uses the shorter internal
 * names. Translating in one place keeps both vocabularies stable.
 */
public final class PosStatus {

    private PosStatus() {
    }

    /** database value -> what the POS screens display */
    public static String toPos(String dbStatus) {
        if (dbStatus == null) {
            return "SCHEDULED";
        }
        switch (dbStatus) {
            case "BOOKED":     return "SCHEDULED";
            case "CHECKED_IN": return "CONFIRMED_BY_NURSE";
            case "COMPLETED":  return "TREATMENT_COMPLETED";
            default:           return dbStatus;
        }
    }

    /** what the POS screens send -> database value */
    public static String toDb(String posStatus) {
        if (posStatus == null) {
            return "BOOKED";
        }
        switch (posStatus.toUpperCase()) {
            case "SCHEDULED":           return "BOOKED";
            case "CONFIRMED_BY_NURSE":  return "CHECKED_IN";
            case "TREATMENT_COMPLETED": return "COMPLETED";
            case "PAID":                return "PAID";
            case "IN_CONSULTATION":     return "IN_CONSULTATION";
            case "CANCELLED":           return "CANCELLED";
            case "NO_SHOW":             return "NO_SHOW";
            default:
                throw new exception.ValidationException("Unknown appointment status: " + posStatus);
        }
    }
}
