package pos;

import exception.ValidationException;

/**
 * The POS front end shows human-readable identifiers (USR-003, SES-2026-004,
 * SRD-APT-0012) while the database uses plain auto-increment integers.
 *
 * This class is the only place that knows how to convert between the two, so
 * the id format can be changed without touching any SQL or any screen.
 */
public final class PosIds {

    private PosIds() {
    }

    public static String user(int id) {
        return String.format("USR-%03d", id);
    }

    public static String treatment(int id) {
        return String.format("TRT-%03d", id);
    }

    public static String session(int id, String sessionDate) {
        String year = sessionDate != null && sessionDate.length() >= 4
                ? sessionDate.substring(0, 4)
                : String.valueOf(java.time.Year.now().getValue());
        return String.format("SES-%s-%03d", year, id);
    }

    public static String appointment(int id) {
        return String.format("SRD-APT-%04d", id);
    }

    public static String report(int id) {
        return String.format("REP-%04d", id);
    }

    /**
     * Pulls the database key back out of a display id. Every format above ends
     * with the numeric key after the last hyphen.
     */
    public static int numeric(Object displayId, String label) {
        if (displayId == null) {
            throw new ValidationException(label + " is required");
        }
        String text = String.valueOf(displayId).trim();
        if (text.isEmpty()) {
            throw new ValidationException(label + " is required");
        }
        int lastDash = text.lastIndexOf('-');
        String tail = lastDash >= 0 ? text.substring(lastDash + 1) : text;
        try {
            return Integer.parseInt(tail);
        } catch (NumberFormatException e) {
            throw new ValidationException(label + " is not a valid identifier: " + text);
        }
    }

    /** Same as {@link #numeric} but returns null instead of failing. */
    public static Integer optionalNumeric(Object displayId) {
        if (displayId == null || String.valueOf(displayId).isBlank()) {
            return null;
        }
        try {
            return numeric(displayId, "id");
        } catch (ValidationException e) {
            return null;
        }
    }
}
