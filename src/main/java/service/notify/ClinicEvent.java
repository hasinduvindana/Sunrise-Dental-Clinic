package service.notify;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** An immutable description of something that just happened in the clinic. */
public class ClinicEvent {

    public enum Type {
        QUEUE_ADVANCED,
        APPOINTMENT_BOOKED,
        APPOINTMENT_CANCELLED,
        BILL_GENERATED,
        PAYMENT_RECEIVED,
        PRESCRIPTION_ISSUED,
        SESSION_STATUS_CHANGED,
        /** Anything the POS screens do that has no more specific type. */
        GENERIC
    }

    private final Type type;
    private final String message;
    private final Map<String, Object> data;
    private final LocalDateTime occurredAt = LocalDateTime.now();

    public ClinicEvent(Type type, String message, Map<String, Object> data) {
        this.type = type;
        this.message = message;
        this.data = data == null ? new LinkedHashMap<>() : data;
    }

    public Type getType() { return type; }
    public String getMessage() { return message; }
    public Map<String, Object> getData() { return data; }
    public LocalDateTime getOccurredAt() { return occurredAt; }

    public Object get(String key) {
        return data.get(key);
    }
}
