package service.notify;

/**
 * DESIGN PATTERN: Observer (listener side).
 * Anything that wants to react to a clinic event implements this.
 */
public interface ClinicEventListener {
    void onEvent(ClinicEvent event);
}
