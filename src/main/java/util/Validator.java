package util;

import exception.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Server-side validation helpers.
 *
 * The browser also validates, but the browser can be bypassed, so every write
 * endpoint re-checks its inputs here. Each failure throws a ValidationException
 * carrying a message the UI can show verbatim.
 */
public final class Validator {

    private static final Pattern PHONE = Pattern.compile("^[0-9+\\-\\s()]{9,20}$");
    private static final Pattern EMAIL = Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[\\w.-]{2,}$");
    private static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9._-]{4,50}$");

    private Validator() { }

    public static String requireText(Map<String, Object> body, String field, int maxLength) {
        Object raw = body.get(field);
        String value = raw == null ? null : String.valueOf(raw).trim();
        if (value == null || value.isEmpty() || "null".equals(value)) {
            throw new ValidationException(label(field) + " is required");
        }
        if (value.length() > maxLength) {
            throw new ValidationException(label(field) + " must be " + maxLength + " characters or fewer");
        }
        return value;
    }

    public static String optionalText(Map<String, Object> body, String field, int maxLength) {
        Object raw = body.get(field);
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty() || "null".equals(value)) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new ValidationException(label(field) + " must be " + maxLength + " characters or fewer");
        }
        return value;
    }

    public static int requireInt(Map<String, Object> body, String field) {
        Object raw = body.get(field);
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new ValidationException(label(field) + " is required");
        }
        try {
            return (int) Double.parseDouble(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(label(field) + " must be a number");
        }
    }

    public static int optionalInt(Map<String, Object> body, String field, int defaultValue) {
        Object raw = body.get(field);
        if (raw == null || String.valueOf(raw).isBlank()) {
            return defaultValue;
        }
        try {
            return (int) Double.parseDouble(String.valueOf(raw).trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(label(field) + " must be a number");
        }
    }

    public static BigDecimal requireMoney(Map<String, Object> body, String field) {
        Object raw = body.get(field);
        if (raw == null || String.valueOf(raw).isBlank()) {
            throw new ValidationException(label(field) + " is required");
        }
        try {
            BigDecimal value = new BigDecimal(String.valueOf(raw).trim());
            if (value.signum() < 0) {
                throw new ValidationException(label(field) + " cannot be negative");
            }
            return value.setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new ValidationException(label(field) + " must be an amount");
        }
    }

    public static BigDecimal optionalMoney(Map<String, Object> body, String field, BigDecimal defaultValue) {
        Object raw = body.get(field);
        if (raw == null || String.valueOf(raw).isBlank()) {
            return defaultValue;
        }
        return requireMoney(body, field);
    }

    public static boolean optionalBoolean(Map<String, Object> body, String field, boolean defaultValue) {
        Object raw = body.get(field);
        if (raw == null) {
            return defaultValue;
        }
        String value = String.valueOf(raw).trim();
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    public static LocalDate requireDate(Map<String, Object> body, String field) {
        String value = requireText(body, field, 20);
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException(label(field) + " must be a date in YYYY-MM-DD format");
        }
    }

    public static LocalDate optionalDate(Map<String, Object> body, String field) {
        String value = optionalText(body, field, 20);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ValidationException(label(field) + " must be a date in YYYY-MM-DD format");
        }
    }

    public static LocalTime requireTime(Map<String, Object> body, String field) {
        String value = requireText(body, field, 10);
        try {
            return LocalTime.parse(value.length() == 5 ? value : value.substring(0, 5));
        } catch (Exception e) {
            throw new ValidationException(label(field) + " must be a time in HH:MM format");
        }
    }

    public static String requireUsername(Map<String, Object> body, String field) {
        String value = requireText(body, field, 50).toLowerCase();
        if (!USERNAME.matcher(value).matches()) {
            throw new ValidationException("Username must be 4-50 characters using letters, digits, dot, dash or underscore");
        }
        return value;
    }

    public static String requirePassword(Map<String, Object> body, String field) {
        String value = requireText(body, field, 100);
        if (value.length() < 6) {
            throw new ValidationException("Password must be at least 6 characters long");
        }
        return value;
    }

    public static String requirePhone(Map<String, Object> body, String field) {
        String value = requireText(body, field, 20);
        if (!PHONE.matcher(value).matches()) {
            throw new ValidationException("Contact number must be 9-20 digits and may contain + - ( ) or spaces");
        }
        return value;
    }

    public static String optionalEmail(Map<String, Object> body, String field) {
        String value = optionalText(body, field, 120);
        if (value != null && !EMAIL.matcher(value).matches()) {
            throw new ValidationException("Email address is not valid");
        }
        return value;
    }

    public static String requireOneOf(Map<String, Object> body, String field, String... allowed) {
        String value = requireText(body, field, 40).toUpperCase();
        for (String option : allowed) {
            if (option.equalsIgnoreCase(value)) {
                return option;
            }
        }
        throw new ValidationException(label(field) + " must be one of " + String.join(", ", allowed));
    }

    /** Turns a camelCase field name into a readable label for error messages. */
    private static String label(String field) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < field.length(); i++) {
            char c = field.charAt(i);
            if (i == 0) {
                sb.append(Character.toUpperCase(c));
            } else if (Character.isUpperCase(c)) {
                sb.append(' ').append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
