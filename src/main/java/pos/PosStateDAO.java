package pos;

import exception.DataAccessException;
import util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read side of the POS API.
 *
 * The POS screens work from one state document rather than a call per table,
 * because almost every screen mixes two or three entities (a queue row needs
 * the appointment, the patient, the doctor and the session all at once).
 * Assembling it server-side in a handful of joined queries is far cheaper than
 * letting the browser stitch it together over a dozen round trips.
 *
 * DESIGN PATTERN: Data Access Object. Every statement for the POS document
 * lives here; nothing above this class contains SQL.
 */
public class PosStateDAO {

    /** Builds the complete document the browser caches. */
    public Map<String, Object> readState() {
        try (Connection cn = DBConnection.getConnection()) {
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("clinicSettings", clinicSettings(cn));
            state.put("users", users(cn));
            state.put("treatmentCatalog", treatments(cn));
            state.put("doctorPricing", doctorPricing(cn));
            state.put("sessions", sessions(cn));
            state.put("patients", patients(cn));
            state.put("appointments", appointments(cn));
            state.put("invoices", invoices(cn));
            state.put("payments", payments(cn));
            state.put("reports", reports(cn));
            return state;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the clinic data", e);
        }
    }

    /**
     * Bookings made on the public portal are attributed to the first active
     * patient administrator, so the audit trail still names a real account.
     */
    public int portalUserId() {
        String sql = "SELECT id FROM users WHERE status = 'ACTIVE' " +
                     "AND role IN ('PATIENT_ADMIN','ADMIN','SUPER_ADMIN') " +
                     "ORDER BY FIELD(role,'PATIENT_ADMIN','ADMIN','SUPER_ADMIN'), id LIMIT 1";
        try (Connection cn = DBConnection.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            throw new DataAccessException("The clinic has no account to record portal bookings against",
                    new SQLException("no portal account"));
        } catch (SQLException e) {
            throw new DataAccessException("Could not identify the portal account", e);
        }
    }

    // -----------------------------------------------------------------
    // Settings
    // -----------------------------------------------------------------

    private Map<String, Object> clinicSettings(Connection cn) throws SQLException {
        Map<String, String> raw = new LinkedHashMap<>();
        try (PreparedStatement ps = cn.prepareStatement("SELECT setting_key, setting_value FROM settings");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                raw.put(rs.getString(1), rs.getString(2));
            }
        }

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("clinicName", raw.getOrDefault("clinic.name", "SunRise Dental Clinic"));
        settings.put("tagline", raw.getOrDefault("clinic.tagline", "Advanced Dental Care & Implant Center"));
        settings.put("logoUrl", raw.getOrDefault("clinic.logo", "assets/images/logo.png"));
        settings.put("iconUrl", raw.getOrDefault("clinic.icon", "assets/images/logo-icon.png"));
        settings.put("phone", raw.getOrDefault("clinic.phone", ""));
        settings.put("email", raw.getOrDefault("clinic.email", ""));
        settings.put("address", raw.getOrDefault("clinic.address", ""));
        settings.put("regNo", raw.getOrDefault("clinic.reg.no", ""));
        settings.put("currency", raw.getOrDefault("billing.currency", "LKR"));
        settings.put("currencySymbol", raw.getOrDefault("billing.currency.symbol", "Rs."));
        settings.put("taxRate", number(raw.get("billing.tax.percent")));
        settings.put("invoicePrefix", raw.getOrDefault("billing.bill.prefix", "SRD-INV-"));
        settings.put("receiptPrefix", raw.getOrDefault("billing.receipt.prefix", "SRD-REC-"));
        settings.put("appointmentPrefix", raw.getOrDefault("billing.appointment.prefix", "SRD-APT-"));
        settings.put("footerNote", raw.getOrDefault("clinic.footer.note", ""));
        return settings;
    }

    // -----------------------------------------------------------------
    // Staff accounts (patients have their own list)
    // -----------------------------------------------------------------

    private List<Map<String, Object>> users(Connection cn) throws SQLException {
        String sql =
                "SELECT u.id, u.username, u.role, u.full_name, u.email, u.phone, u.status, " +
                "       dp.specialization, dp.qualification, dp.consultation_fee, dp.room_no " +
                "FROM users u " +
                "LEFT JOIN doctor_profiles dp ON dp.user_id = u.id " +
                "WHERE u.role <> 'PATIENT' " +
                "ORDER BY u.id";

        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> user = new LinkedHashMap<>();
                user.put("id", PosIds.user(rs.getInt("id")));
                user.put("username", rs.getString("username"));
                user.put("role", rs.getString("role"));
                user.put("fullName", rs.getString("full_name"));
                user.put("email", rs.getString("email"));
                user.put("phone", rs.getString("phone"));
                user.put("status", rs.getString("status"));
                if ("DOCTOR".equals(rs.getString("role"))) {
                    user.put("specialty", rs.getString("specialization"));
                    user.put("qualification", rs.getString("qualification"));
                    user.put("consultationFee", money(rs.getBigDecimal("consultation_fee")));
                    user.put("roomNo", rs.getString("room_no"));
                }
                rows.add(user);
            }
        }
        return rows;
    }

    // -----------------------------------------------------------------
    // Treatment catalogue and per-doctor charges
    // -----------------------------------------------------------------

    private List<Map<String, Object>> treatments(Connection cn) throws SQLException {
        String sql = "SELECT id, code, name, category, description, base_price " +
                     "FROM treatments WHERE status = 'ACTIVE' ORDER BY id";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> t = new LinkedHashMap<>();
                t.put("id", PosIds.treatment(rs.getInt("id")));
                t.put("code", rs.getString("code"));
                t.put("name", rs.getString("name"));
                t.put("category", rs.getString("category"));
                t.put("description", rs.getString("description"));
                t.put("defaultFee", money(rs.getBigDecimal("base_price")));
                rows.add(t);
            }
        }
        return rows;
    }

    private List<Map<String, Object>> doctorPricing(Connection cn) throws SQLException {
        String sql = "SELECT doctor_id, treatment_id, custom_fee FROM doctor_treatment_pricing";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("doctorId", PosIds.user(rs.getInt("doctor_id")));
                p.put("treatmentId", PosIds.treatment(rs.getInt("treatment_id")));
                p.put("customFee", money(rs.getBigDecimal("custom_fee")));
                rows.add(p);
            }
        }
        return rows;
    }

    // -----------------------------------------------------------------
    // Sessions - booked count comes from the appointments table so the
    // number on screen can never drift away from reality.
    // -----------------------------------------------------------------

    private List<Map<String, Object>> sessions(Connection cn) throws SQLException {
        String sql =
                "SELECT s.id, s.doctor_id, s.session_date, s.start_time, s.end_time, s.room_no, " +
                "       s.max_patients, s.consultation_fee, s.current_queue_no, s.status, " +
                "       u.full_name AS doctor_name, dp.specialization, " +
                "       (SELECT COUNT(*) FROM appointments a " +
                "         WHERE a.session_id = s.id AND a.status <> 'CANCELLED') AS booked_count " +
                "FROM doctor_sessions s " +
                "JOIN users u ON u.id = s.doctor_id " +
                "LEFT JOIN doctor_profiles dp ON dp.user_id = s.doctor_id " +
                "ORDER BY s.session_date DESC, s.start_time";

        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String date = String.valueOf(rs.getDate("session_date"));
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("id", PosIds.session(rs.getInt("id"), date));
                s.put("doctorId", PosIds.user(rs.getInt("doctor_id")));
                s.put("doctorName", rs.getString("doctor_name"));
                s.put("specialty", rs.getString("specialization"));
                s.put("date", date);
                s.put("startTime", shortTime(rs.getString("start_time")));
                s.put("endTime", shortTime(rs.getString("end_time")));
                s.put("roomNo", rs.getString("room_no"));
                s.put("maxPatients", rs.getInt("max_patients"));
                s.put("bookedCount", rs.getInt("booked_count"));
                s.put("consultationFee", money(rs.getBigDecimal("consultation_fee")));
                s.put("currentToken", rs.getInt("current_queue_no"));
                s.put("status", rs.getString("status"));
                rows.add(s);
            }
        }
        return rows;
    }

    // -----------------------------------------------------------------
    // Patients - keyed by NIC on every POS screen
    // -----------------------------------------------------------------

    private List<Map<String, Object>> patients(Connection cn) throws SQLException {
        String sql =
                "SELECT id, patient_no, nic, full_name, date_of_birth, gender, contact, email, " +
                "       address, blood_group, allergies, medical_history, is_vip, created_at " +
                "FROM patients ORDER BY id";

        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("nic", rs.getString("nic"));
                p.put("patientNo", rs.getString("patient_no"));
                p.put("fullName", rs.getString("full_name"));
                p.put("dob", rs.getDate("date_of_birth") == null ? null : String.valueOf(rs.getDate("date_of_birth")));
                p.put("gender", properCase(rs.getString("gender")));
                p.put("phone", rs.getString("contact"));
                p.put("email", rs.getString("email"));
                p.put("address", rs.getString("address"));
                p.put("bloodGroup", rs.getString("blood_group"));
                p.put("allergies", rs.getString("allergies"));
                p.put("medicalHistory", rs.getString("medical_history"));
                p.put("vip", rs.getBoolean("is_vip"));
                p.put("registeredDate", day(rs.getString("created_at")));
                rows.add(p);
            }
        }
        return rows;
    }

    // -----------------------------------------------------------------
    // Appointments - one flat row carrying everything the queue,
    // triage and cashier screens read.
    // -----------------------------------------------------------------

    private List<Map<String, Object>> appointments(Connection cn) throws SQLException {
        String sql =
                "SELECT a.id, a.appointment_no, a.session_id, a.queue_no, a.time_slot, a.status, " +
                "       a.notes, a.vitals_bp, a.vitals_pulse, a.chief_complaint, a.cancel_reason, " +
                "       a.receipt_no, a.created_at, " +
                "       s.session_date, s.consultation_fee, s.doctor_id, " +
                "       u.full_name AS doctor_name, " +
                "       p.nic, p.full_name AS patient_name, p.contact AS patient_phone " +
                "FROM appointments a " +
                "JOIN doctor_sessions s ON s.id = a.session_id " +
                "JOIN users u           ON u.id = s.doctor_id " +
                "JOIN patients p        ON p.id = a.patient_id " +
                "ORDER BY a.id";

        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String sessionDate = String.valueOf(rs.getDate("session_date"));
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("id", PosIds.appointment(rs.getInt("id")));
                a.put("appointmentNo", rs.getString("appointment_no"));
                a.put("tokenNumber", rs.getInt("queue_no"));
                a.put("sessionId", PosIds.session(rs.getInt("session_id"), sessionDate));
                a.put("doctorId", PosIds.user(rs.getInt("doctor_id")));
                a.put("doctorName", rs.getString("doctor_name"));
                a.put("patientNic", rs.getString("nic"));
                a.put("patientName", rs.getString("patient_name"));
                a.put("patientPhone", rs.getString("patient_phone"));
                a.put("date", sessionDate);
                a.put("timeSlot", rs.getString("time_slot"));
                a.put("consultationFee", money(rs.getBigDecimal("consultation_fee")));
                a.put("status", PosStatus.toPos(rs.getString("status")));
                a.put("notes", rs.getString("notes"));
                a.put("cancelReason", rs.getString("cancel_reason"));
                a.put("paymentReceiptNo", rs.getString("receipt_no"));
                a.put("createdAt", minute(rs.getString("created_at")));

                String bp = rs.getString("vitals_bp");
                String pulse = rs.getString("vitals_pulse");
                String complaint = rs.getString("chief_complaint");
                if (bp == null && pulse == null && complaint == null) {
                    a.put("vitals", null);
                } else {
                    Map<String, Object> vitals = new LinkedHashMap<>();
                    vitals.put("bp", bp);
                    vitals.put("pulse", pulse);
                    vitals.put("chiefComplaint", complaint);
                    a.put("vitals", vitals);
                }
                rows.add(a);
            }
        }
        return rows;
    }

    // -----------------------------------------------------------------
    // Invoices (bills) with their lines
    // -----------------------------------------------------------------

    private List<Map<String, Object>> invoices(Connection cn) throws SQLException {
        Map<Integer, List<Map<String, Object>>> itemsByBill = new LinkedHashMap<>();
        String itemSql = "SELECT bill_id, description, line_total FROM bill_items ORDER BY id";
        try (PreparedStatement ps = cn.prepareStatement(itemSql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("description", rs.getString("description"));
                item.put("amount", money(rs.getBigDecimal("line_total")));
                itemsByBill.computeIfAbsent(rs.getInt("bill_id"), k -> new ArrayList<>()).add(item);
            }
        }

        String sql =
                "SELECT b.id, b.bill_no, b.appointment_id, b.consultation_fee, b.treatment_total, " +
                "       b.discount, b.tax, b.total, b.status, b.created_at, b.doctor_id, " +
                "       u.full_name AS doctor_name, p.nic, p.full_name AS patient_name, " +
                "       (SELECT pay.receipt_no FROM payments pay WHERE pay.bill_id = b.id " +
                "         ORDER BY pay.id DESC LIMIT 1) AS payment_ref, " +
                "       (SELECT t.name FROM bill_items bi LEFT JOIN treatments t ON t.id = bi.treatment_id " +
                "         WHERE bi.bill_id = b.id AND bi.treatment_id IS NOT NULL LIMIT 1) AS treatment_type " +
                "FROM bills b " +
                "JOIN users u    ON u.id = b.doctor_id " +
                "JOIN patients p ON p.id = b.patient_id " +
                "ORDER BY b.id";

        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BigDecimal consult = rs.getBigDecimal("consultation_fee");
                BigDecimal treatments = rs.getBigDecimal("treatment_total");

                Map<String, Object> inv = new LinkedHashMap<>();
                inv.put("invoiceNo", rs.getString("bill_no"));
                inv.put("appointmentId", PosIds.appointment(rs.getInt("appointment_id")));
                inv.put("patientNic", rs.getString("nic"));
                inv.put("patientName", rs.getString("patient_name"));
                inv.put("doctorId", PosIds.user(rs.getInt("doctor_id")));
                inv.put("doctorName", rs.getString("doctor_name"));
                inv.put("treatmentType", rs.getString("treatment_type") == null
                        ? "Consultation" : rs.getString("treatment_type"));
                inv.put("items", itemsByBill.getOrDefault(rs.getInt("id"), new ArrayList<>()));
                inv.put("subtotal", money(consult.add(treatments)));
                inv.put("tax", money(rs.getBigDecimal("tax")));
                inv.put("discount", money(rs.getBigDecimal("discount")));
                inv.put("totalAmount", money(rs.getBigDecimal("total")));
                inv.put("status", rs.getString("status"));
                inv.put("createdAt", minute(rs.getString("created_at")));
                inv.put("paymentRef", rs.getString("payment_ref"));
                rows.add(inv);
            }
        }
        return rows;
    }

    // -----------------------------------------------------------------
    // Payments taken at the counter
    // -----------------------------------------------------------------

    private List<Map<String, Object>> payments(Connection cn) throws SQLException {
        String sql =
                "SELECT pay.receipt_no, pay.amount, pay.method, pay.card_type, pay.card_provider, " +
                "       pay.card_masked, pay.bank_name, pay.paid_at, " +
                "       b.bill_no, b.appointment_id, b.doctor_id, " +
                "       doc.full_name AS doctor_name, p.nic, p.full_name AS patient_name, " +
                "       cashier.full_name AS cashier_name " +
                "FROM payments pay " +
                "JOIN bills b        ON b.id = pay.bill_id " +
                "JOIN users doc      ON doc.id = b.doctor_id " +
                "JOIN patients p     ON p.id = b.patient_id " +
                "LEFT JOIN users cashier ON cashier.id = pay.received_by " +
                "ORDER BY pay.id";

        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> pay = new LinkedHashMap<>();
                pay.put("receiptNo", rs.getString("receipt_no"));
                pay.put("invoiceNo", rs.getString("bill_no"));
                pay.put("appointmentId", PosIds.appointment(rs.getInt("appointment_id")));
                pay.put("patientNic", rs.getString("nic"));
                pay.put("patientName", rs.getString("patient_name"));
                pay.put("doctorId", PosIds.user(rs.getInt("doctor_id")));
                pay.put("doctorName", rs.getString("doctor_name"));
                pay.put("paymentType", rs.getString("method"));
                pay.put("cardType", rs.getString("card_type"));
                pay.put("cardProvider", rs.getString("card_provider"));
                pay.put("cardNumberMasked", rs.getString("card_masked"));
                pay.put("bankName", rs.getString("bank_name"));
                pay.put("amountPaid", money(rs.getBigDecimal("amount")));
                pay.put("cashierName", rs.getString("cashier_name"));
                pay.put("timestamp", second(rs.getString("paid_at")));
                rows.add(pay);
            }
        }
        return rows;
    }

    // -----------------------------------------------------------------
    // Diagnostic reports
    // -----------------------------------------------------------------

    private List<Map<String, Object>> reports(Connection cn) throws SQLException {
        String sql =
                "SELECT r.id, r.report_no, r.report_type, r.report_date, r.findings, r.status, " +
                "       r.file_name, r.title, r.doctor_id, " +
                "       u.full_name AS doctor_name, p.nic, p.full_name AS patient_name " +
                "FROM medical_reports r " +
                "LEFT JOIN users u ON u.id = r.doctor_id " +
                "JOIN patients p   ON p.id = r.patient_id " +
                "ORDER BY r.id";

        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = cn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> r = new LinkedHashMap<>();
                int id = rs.getInt("id");
                r.put("id", PosIds.report(id));
                r.put("reportNo", rs.getString("report_no"));
                r.put("patientNic", rs.getString("nic"));
                r.put("patientName", rs.getString("patient_name"));
                r.put("doctorId", rs.getObject("doctor_id") == null ? null : PosIds.user(rs.getInt("doctor_id")));
                r.put("doctorName", rs.getString("doctor_name"));
                r.put("reportType", rs.getString("report_type"));
                r.put("date", rs.getDate("report_date") == null ? null : String.valueOf(rs.getDate("report_date")));
                r.put("findings", rs.getString("findings"));
                r.put("fileName", rs.getString("file_name"));
                r.put("fileType", "pdf");
                r.put("status", rs.getString("status"));
                r.put("downloadUrl", rs.getString("file_name") == null
                        ? null : "api/medical-reports/" + id + "/file");
                rows.add(r);
            }
        }
        return rows;
    }

    // -----------------------------------------------------------------
    // Formatting helpers - the browser wants plain numbers and strings
    // -----------------------------------------------------------------

    private double money(BigDecimal value) {
        return value == null ? 0d : value.doubleValue();
    }

    private double number(String value) {
        try {
            return value == null ? 0d : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    /** "09:00:00" -> "09:00" */
    private String shortTime(String time) {
        return time != null && time.length() >= 5 ? time.substring(0, 5) : time;
    }

    /** "2026-09-01 10:30:00.0" -> "2026-09-01" */
    private String day(String timestamp) {
        return timestamp != null && timestamp.length() >= 10 ? timestamp.substring(0, 10) : timestamp;
    }

    /** "2026-09-01 10:30:00.0" -> "2026-09-01 10:30" */
    private String minute(String timestamp) {
        return timestamp != null && timestamp.length() >= 16 ? timestamp.substring(0, 16) : timestamp;
    }

    /** "2026-09-01 10:30:00.0" -> "2026-09-01 10:30:00" */
    private String second(String timestamp) {
        return timestamp != null && timestamp.length() >= 19 ? timestamp.substring(0, 19) : timestamp;
    }

    private String properCase(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return value.charAt(0) + value.substring(1).toLowerCase();
    }
}
