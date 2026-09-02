package util;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds the human-readable reference numbers printed on paperwork:
 * patients (PAT-2026-0007), appointments (APT-20260901-0031) and bills
 * (SDC-20260901-0018). The numeric part comes from the row count already in
 * the table, so numbers stay sequential per day.
 */
public final class IdGenerator {

    private static final AtomicInteger FALLBACK = new AtomicInteger(0);

    private IdGenerator() { }

    public static String patientNo(int sequence) {
        return String.format("PAT-%d-%04d", LocalDate.now().getYear(), sequence);
    }

    public static String appointmentNo(LocalDate sessionDate, int sequence) {
        return String.format("APT-%s-%04d", compact(sessionDate), sequence);
    }

    public static String billNo(String prefix, int sequence) {
        return String.format("%s-%s-%04d", prefix, compact(LocalDate.now()), sequence);
    }

    public static String fileToken() {
        return System.currentTimeMillis() + "-" + FALLBACK.incrementAndGet();
    }

    private static String compact(LocalDate date) {
        LocalDate d = date == null ? LocalDate.now() : date;
        return String.format("%04d%02d%02d", d.getYear(), d.getMonthValue(), d.getDayOfMonth());
    }
}
