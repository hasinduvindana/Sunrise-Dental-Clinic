package pos;

import exception.ConflictException;
import exception.DataAccessException;
import exception.NotFoundException;
import exception.ValidationException;
import model.Role;
import model.User;
import util.DBConnection;
import util.IdGenerator;
import util.PasswordUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Write side of the POS API. Each public method is one thing a member of staff
 * can do at a screen, and each runs in a single transaction so a half-finished
 * checkout can never be left in the database.
 *
 * DESIGN PATTERN: Data Access Object.
 */
public class PosCommandDAO {

    // =================================================================
    // Staff accounts
    // =================================================================

    public String saveUser(Map<String, Object> body, User actor) {
        String idText = text(body, "id");
        boolean isNew = idText == null;

        try (Connection cn = DBConnection.getConnection()) {
            cn.setAutoCommit(false);
            try {
                int userId;
                Role role = Role.of(required(body, "role", "Role"));
                if (role == null) {
                    throw new ValidationException("Choose a valid role for the account");
                }

                if (isNew) {
                    String username = required(body, "username", "Username").toLowerCase();
                    String password = text(body, "password");
                    if (password == null || password.length() < 3) {
                        throw new ValidationException("Give the new account a password of at least 3 characters");
                    }
                    if (usernameTaken(cn, username, 0)) {
                        throw new ConflictException("That username is already in use");
                    }
                    String salt = PasswordUtil.newSalt();

                    String sql = "INSERT INTO users (username, password_hash, salt, role, full_name, " +
                                 "email, phone, status, created_by) VALUES (?,?,?,?,?,?,?,?,?)";
                    try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, username);
                        ps.setString(2, PasswordUtil.hash(password, salt));
                        ps.setString(3, salt);
                        ps.setString(4, role.name());
                        ps.setString(5, required(body, "fullName", "Full name"));
                        ps.setString(6, text(body, "email"));
                        ps.setString(7, text(body, "phone"));
                        ps.setString(8, textOr(body, "status", "ACTIVE"));
                        ps.setInt(9, actor.getId());
                        ps.executeUpdate();
                        userId = generatedKey(ps);
                    }
                } else {
                    userId = PosIds.numeric(idText, "User id");
                    String sql = "UPDATE users SET full_name = ?, email = ?, phone = ?, role = ?, status = ? " +
                                 "WHERE id = ?";
                    try (PreparedStatement ps = cn.prepareStatement(sql)) {
                        ps.setString(1, required(body, "fullName", "Full name"));
                        ps.setString(2, text(body, "email"));
                        ps.setString(3, text(body, "phone"));
                        ps.setString(4, role.name());
                        ps.setString(5, textOr(body, "status", "ACTIVE"));
                        ps.setInt(6, userId);
                        if (ps.executeUpdate() == 0) {
                            throw new NotFoundException("That staff account no longer exists");
                        }
                    }
                    String newPassword = text(body, "password");
                    if (newPassword != null && !newPassword.isBlank()) {
                        String salt = PasswordUtil.newSalt();
                        try (PreparedStatement ps = cn.prepareStatement(
                                "UPDATE users SET password_hash = ?, salt = ? WHERE id = ?")) {
                            ps.setString(1, PasswordUtil.hash(newPassword, salt));
                            ps.setString(2, salt);
                            ps.setInt(3, userId);
                            ps.executeUpdate();
                        }
                    }
                }

                if (role == Role.DOCTOR) {
                    saveDoctorProfile(cn, userId, body);
                }

                cn.commit();
                return PosIds.user(userId);
            } catch (SQLException | RuntimeException e) {
                rollback(cn);
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the staff account", e);
        }
    }

    private void saveDoctorProfile(Connection cn, int userId, Map<String, Object> body) throws SQLException {
        String sql = "INSERT INTO doctor_profiles (user_id, specialization, qualification, " +
                     "consultation_fee, room_no) VALUES (?,?,?,?,?) " +
                     "ON DUPLICATE KEY UPDATE specialization = VALUES(specialization), " +
                     "qualification = VALUES(qualification), consultation_fee = VALUES(consultation_fee), " +
                     "room_no = VALUES(room_no)";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, textOr(body, "specialty", "General Dentistry"));
            ps.setString(3, text(body, "qualification"));
            ps.setBigDecimal(4, decimal(body, "consultationFee", new BigDecimal("2500")));
            ps.setString(5, text(body, "roomNo"));
            ps.executeUpdate();
        }
    }

    public void setUserStatus(Object userId, String status) {
        String value = "ACTIVE".equalsIgnoreCase(status) ? "ACTIVE" : "INACTIVE";
        update("UPDATE users SET status = ? WHERE id = ?",
                "Could not change the account status",
                ps -> {
                    ps.setString(1, value);
                    ps.setInt(2, PosIds.numeric(userId, "User id"));
                });
    }

    // =================================================================
    // Patients
    // =================================================================

    /** Registers a patient, or updates the existing record with the same NIC. */
    public String savePatient(Map<String, Object> body, User actor) {
        String nic = required(body, "nic", "NIC");

        try (Connection cn = DBConnection.getConnection()) {
            cn.setAutoCommit(false);
            try {
                Integer existingId = patientIdByNic(cn, nic);

                if (existingId == null) {
                    String patientNo = IdGenerator.patientNo(nextPatientSequence(cn));
                    String sql = "INSERT INTO patients (patient_no, nic, full_name, date_of_birth, gender, " +
                                 "contact, email, address, blood_group, allergies, medical_history, " +
                                 "registered_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
                    try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setString(1, patientNo);
                        ps.setString(2, nic);
                        ps.setString(3, required(body, "fullName", "Patient name"));
                        setDate(ps, 4, text(body, "dob"));
                        ps.setString(5, gender(text(body, "gender")));
                        ps.setString(6, required(body, "phone", "Contact number"));
                        ps.setString(7, text(body, "email"));
                        ps.setString(8, text(body, "address"));
                        ps.setString(9, textOr(body, "bloodGroup", "N/A"));
                        ps.setString(10, textOr(body, "allergies", "None"));
                        ps.setString(11, text(body, "medicalHistory"));
                        ps.setInt(12, actor.getId());
                        ps.executeUpdate();
                        existingId = generatedKey(ps);
                    }
                } else {
                    String sql = "UPDATE patients SET full_name = ?, date_of_birth = ?, gender = ?, " +
                                 "contact = ?, email = ?, address = ?, blood_group = ?, allergies = ?, " +
                                 "medical_history = ? WHERE id = ?";
                    try (PreparedStatement ps = cn.prepareStatement(sql)) {
                        ps.setString(1, required(body, "fullName", "Patient name"));
                        setDate(ps, 2, text(body, "dob"));
                        ps.setString(3, gender(text(body, "gender")));
                        ps.setString(4, required(body, "phone", "Contact number"));
                        ps.setString(5, text(body, "email"));
                        ps.setString(6, text(body, "address"));
                        ps.setString(7, textOr(body, "bloodGroup", "N/A"));
                        ps.setString(8, textOr(body, "allergies", "None"));
                        ps.setString(9, text(body, "medicalHistory"));
                        ps.setInt(10, existingId);
                        ps.executeUpdate();
                    }
                }

                cn.commit();
                return nic;
            } catch (SQLException | RuntimeException e) {
                rollback(cn);
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the patient record", e);
        }
    }

    // =================================================================
    // Sessions
    // =================================================================

    public String saveSession(Map<String, Object> body, User actor) {
        String idText = text(body, "id");
        int doctorId = actor.getRole() == Role.DOCTOR
                ? actor.getId()
                : PosIds.numeric(required(body, "doctorId", "Doctor"), "Doctor id");
        String date = required(body, "date", "Session date");

        try (Connection cn = DBConnection.getConnection()) {
            cn.setAutoCommit(false);
            try {
                BigDecimal fee = decimal(body, "consultationFee", doctorFee(cn, doctorId));
                int sessionId;

                if (idText == null) {
                    String sql = "INSERT INTO doctor_sessions (doctor_id, session_date, start_time, " +
                                 "end_time, room_no, max_patients, consultation_fee, status) " +
                                 "VALUES (?,?,?,?,?,?,?,?)";
                    try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                        ps.setInt(1, doctorId);
                        ps.setString(2, date);
                        ps.setString(3, required(body, "startTime", "Start time"));
                        ps.setString(4, required(body, "endTime", "End time"));
                        ps.setString(5, textOr(body, "roomNo", doctorRoom(cn, doctorId)));
                        ps.setInt(6, intOr(body, "maxPatients", 12));
                        ps.setBigDecimal(7, fee);
                        ps.setString(8, textOr(body, "status", "ACTIVE"));
                        ps.executeUpdate();
                        sessionId = generatedKey(ps);
                    }
                } else {
                    sessionId = PosIds.numeric(idText, "Session id");
                    String sql = "UPDATE doctor_sessions SET session_date = ?, start_time = ?, end_time = ?, " +
                                 "room_no = ?, max_patients = ?, consultation_fee = ?, status = ? WHERE id = ?";
                    try (PreparedStatement ps = cn.prepareStatement(sql)) {
                        ps.setString(1, date);
                        ps.setString(2, required(body, "startTime", "Start time"));
                        ps.setString(3, required(body, "endTime", "End time"));
                        ps.setString(4, text(body, "roomNo"));
                        ps.setInt(5, intOr(body, "maxPatients", 12));
                        ps.setBigDecimal(6, fee);
                        ps.setString(7, textOr(body, "status", "ACTIVE"));
                        ps.setInt(8, sessionId);
                        if (ps.executeUpdate() == 0) {
                            throw new NotFoundException("That session no longer exists");
                        }
                    }
                }

                cn.commit();
                return PosIds.session(sessionId, date);
            } catch (SQLException | RuntimeException e) {
                rollback(cn);
                throw e;
            }
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("uq_session_slot")) {
                throw new ConflictException("That doctor already has a session starting at this time");
            }
            throw new DataAccessException("Could not save the session", e);
        }
    }

    // =================================================================
    // Appointments
    // =================================================================

    /**
     * Books a patient into a session. The queue number is allocated by the
     * database trigger, which is also what refuses the booking when the
     * session is full, so two clerks booking the last slot cannot both win.
     */
    public Map<String, Object> bookAppointment(Map<String, Object> body, User actor) {
        int sessionId = PosIds.numeric(required(body, "sessionId", "Session"), "Session id");
        String nic = required(body, "patientNic", "Patient NIC");

        try (Connection cn = DBConnection.getConnection()) {
            cn.setAutoCommit(false);
            try {
                Integer patientId = patientIdByNic(cn, nic);
                if (patientId == null) {
                    throw new NotFoundException("No patient is registered with NIC " + nic);
                }

                String appointmentNo = IdGenerator.appointmentNo(LocalDate.now(), nextAppointmentSequence(cn));
                int appointmentId;

                String sql = "INSERT INTO appointments (appointment_no, session_id, patient_id, " +
                             "time_slot, status, notes, booked_by) VALUES (?,?,?,?,?,?,?)";
                try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, appointmentNo);
                    ps.setInt(2, sessionId);
                    ps.setInt(3, patientId);
                    ps.setString(4, text(body, "timeSlot"));
                    ps.setString(5, "BOOKED");
                    ps.setString(6, text(body, "notes"));
                    ps.setInt(7, actor.getId());
                    ps.executeUpdate();
                    appointmentId = generatedKey(ps);
                }

                int token;
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT queue_no FROM appointments WHERE id = ?")) {
                    ps.setInt(1, appointmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        token = rs.next() ? rs.getInt(1) : 0;
                    }
                }

                cn.commit();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("id", PosIds.appointment(appointmentId));
                result.put("appointmentNo", appointmentNo);
                result.put("tokenNumber", token);
                return result;
            } catch (SQLException | RuntimeException e) {
                rollback(cn);
                throw e;
            }
        } catch (SQLException e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            if (message.contains("uq_appt_patient_session")) {
                throw new ConflictException("That patient is already booked into this session");
            }
            if (message.contains("full") || message.contains("closed")) {
                throw new ConflictException(message.substring(message.indexOf(':') + 1).trim());
            }
            throw new DataAccessException("Could not book the appointment", e);
        }
    }

    /** Moves an appointment along the queue, optionally recording triage vitals. */
    public void updateAppointmentStatus(Map<String, Object> body, User actor) {
        int appointmentId = PosIds.numeric(required(body, "appointmentId", "Appointment"), "Appointment id");
        String dbStatus = PosStatus.toDb(required(body, "status", "Status"));

        @SuppressWarnings("unchecked")
        Map<String, Object> vitals = body.get("vitals") instanceof Map
                ? (Map<String, Object>) body.get("vitals") : null;

        try (Connection cn = DBConnection.getConnection()) {
            cn.setAutoCommit(false);
            try {
                StringBuilder sql = new StringBuilder("UPDATE appointments SET status = ?");
                if (vitals != null) {
                    sql.append(", vitals_bp = ?, vitals_pulse = ?, chief_complaint = ?, ")
                       .append("triaged_by = ?, triaged_at = CURRENT_TIMESTAMP");
                }
                if (text(body, "cancelReason") != null) {
                    sql.append(", cancel_reason = ?");
                }
                if (text(body, "receiptNo") != null) {
                    sql.append(", receipt_no = ?");
                }
                sql.append(" WHERE id = ?");

                try (PreparedStatement ps = cn.prepareStatement(sql.toString())) {
                    int i = 1;
                    ps.setString(i++, dbStatus);
                    if (vitals != null) {
                        ps.setString(i++, str(vitals.get("bp")));
                        ps.setString(i++, str(vitals.get("pulse")));
                        ps.setString(i++, str(vitals.get("chiefComplaint")));
                        ps.setInt(i++, actor.getId());
                    }
                    if (text(body, "cancelReason") != null) {
                        ps.setString(i++, text(body, "cancelReason"));
                    }
                    if (text(body, "receiptNo") != null) {
                        ps.setString(i++, text(body, "receiptNo"));
                    }
                    ps.setInt(i, appointmentId);
                    if (ps.executeUpdate() == 0) {
                        throw new NotFoundException("That appointment no longer exists");
                    }
                }
                cn.commit();
            } catch (SQLException | RuntimeException e) {
                rollback(cn);
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the appointment", e);
        }
    }

    /** Cancels every open appointment held by one NIC - the cashier's cancel desk. */
    public int cancelByNic(String nic, String reason) {
        try (Connection cn = DBConnection.getConnection()) {
            String sql = "UPDATE appointments a " +
                         "JOIN patients p ON p.id = a.patient_id " +
                         "SET a.status = 'CANCELLED', a.cancel_reason = ? " +
                         "WHERE p.nic = ? AND a.status NOT IN ('CANCELLED','COMPLETED')";
            try (PreparedStatement ps = cn.prepareStatement(sql)) {
                ps.setString(1, reason == null ? "Cancelled at the front desk" : reason);
                ps.setString(2, nic);
                return ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not cancel the appointments", e);
        }
    }

    // =================================================================
    // Invoices and payments
    // =================================================================

    @SuppressWarnings("unchecked")
    public String createInvoice(Map<String, Object> body, User actor) {
        int appointmentId = PosIds.numeric(required(body, "appointmentId", "Appointment"), "Appointment id");
        List<Object> items = body.get("items") instanceof List
                ? (List<Object>) body.get("items") : List.of();
        if (items.isEmpty()) {
            throw new ValidationException("An invoice needs at least one line");
        }

        try (Connection cn = DBConnection.getConnection()) {
            cn.setAutoCommit(false);
            try {
                int patientId;
                int doctorId;
                BigDecimal consultationFee;
                try (PreparedStatement ps = cn.prepareStatement(
                        "SELECT a.patient_id, s.doctor_id, s.consultation_fee " +
                        "FROM appointments a JOIN doctor_sessions s ON s.id = a.session_id WHERE a.id = ?")) {
                    ps.setInt(1, appointmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new NotFoundException("That appointment no longer exists");
                        }
                        patientId = rs.getInt(1);
                        doctorId = rs.getInt(2);
                        consultationFee = rs.getBigDecimal(3);
                    }
                }

                BigDecimal treatmentTotal = BigDecimal.ZERO;
                for (Object raw : items) {
                    Map<String, Object> item = (Map<String, Object>) raw;
                    treatmentTotal = treatmentTotal.add(decimal(item, "amount", BigDecimal.ZERO));
                }
                // The consultation fee is charged as its own line, so it is not
                // counted twice in the treatment subtotal.
                treatmentTotal = treatmentTotal.subtract(consultationFee).max(BigDecimal.ZERO);

                BigDecimal discount = decimal(body, "discount", BigDecimal.ZERO);
                BigDecimal tax = decimal(body, "tax", BigDecimal.ZERO);
                BigDecimal total = consultationFee.add(treatmentTotal).subtract(discount).add(tax);

                String billNo = IdGenerator.billNo(billPrefix(cn), nextBillSequence(cn));
                int billId;

                String sql = "INSERT INTO bills (bill_no, appointment_id, patient_id, doctor_id, " +
                             "consultation_fee, treatment_total, discount, tax, total, pricing_strategy, " +
                             "status, generated_by) VALUES (?,?,?,?,?,?,?,?,?,?, 'PENDING', ?)";
                try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, billNo);
                    ps.setInt(2, appointmentId);
                    ps.setInt(3, patientId);
                    ps.setInt(4, doctorId);
                    ps.setBigDecimal(5, consultationFee);
                    ps.setBigDecimal(6, treatmentTotal);
                    ps.setBigDecimal(7, discount);
                    ps.setBigDecimal(8, tax);
                    ps.setBigDecimal(9, total);
                    ps.setString(10, textOr(body, "pricingStrategy", "STANDARD"));
                    ps.setInt(11, actor.getId());
                    ps.executeUpdate();
                    billId = generatedKey(ps);
                }

                String itemSql = "INSERT INTO bill_items (bill_id, treatment_id, description, quantity, " +
                                 "unit_price, line_total) VALUES (?,?,?,1,?,?)";
                try (PreparedStatement ps = cn.prepareStatement(itemSql)) {
                    for (Object raw : items) {
                        Map<String, Object> item = (Map<String, Object>) raw;
                        BigDecimal amount = decimal(item, "amount", BigDecimal.ZERO);
                        ps.setInt(1, billId);
                        Integer treatmentId = PosIds.optionalNumeric(item.get("treatmentId"));
                        if (treatmentId == null) {
                            ps.setNull(2, Types.INTEGER);
                        } else {
                            ps.setInt(2, treatmentId);
                        }
                        ps.setString(3, str(item.get("description")));
                        ps.setBigDecimal(4, amount);
                        ps.setBigDecimal(5, amount);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                cn.commit();
                return billNo;
            } catch (SQLException | RuntimeException e) {
                rollback(cn);
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not create the invoice", e);
        }
    }

    /**
     * Takes a payment at the counter. A card number is masked before it is
     * stored - the clinic keeps the last four digits only, never the full PAN.
     */
    public Map<String, Object> processPayment(Map<String, Object> body, User actor) {
        String invoiceNo = text(body, "invoiceNo");
        Integer appointmentId = PosIds.optionalNumeric(body.get("appointmentId"));
        BigDecimal amount = decimal(body, "amountPaid", null);
        if (amount == null || amount.signum() <= 0) {
            throw new ValidationException("Enter the amount being paid");
        }
        String method = textOr(body, "paymentType", "CASH").toUpperCase();
        if (!method.equals("CASH") && !method.equals("CARD") && !method.equals("ONLINE")) {
            throw new ValidationException("Payment type must be CASH, CARD or ONLINE");
        }

        try (Connection cn = DBConnection.getConnection()) {
            cn.setAutoCommit(false);
            try {
                Integer billId = null;
                if (invoiceNo != null) {
                    billId = billIdByNo(cn, invoiceNo);
                    if (billId == null) {
                        throw new NotFoundException("Invoice " + invoiceNo + " was not found");
                    }
                } else if (appointmentId != null) {
                    // Paying the consultation fee before an invoice exists:
                    // raise a one-line invoice for it so every payment has a bill.
                    billId = consultationBill(cn, appointmentId, amount, actor);
                    try (PreparedStatement ps = cn.prepareStatement("SELECT bill_no FROM bills WHERE id = ?")) {
                        ps.setInt(1, billId);
                        try (ResultSet rs = ps.executeQuery()) {
                            invoiceNo = rs.next() ? rs.getString(1) : null;
                        }
                    }
                } else {
                    throw new ValidationException("A payment needs either an invoice or an appointment");
                }

                String receiptNo = IdGenerator.billNo(receiptPrefix(cn), nextPaymentSequence(cn));
                String masked = maskCard(text(body, "cardNumber"));

                String sql = "INSERT INTO payments (receipt_no, bill_id, amount, method, card_type, " +
                             "card_provider, card_masked, bank_name, reference, received_by) " +
                             "VALUES (?,?,?,?,?,?,?,?,?,?)";
                try (PreparedStatement ps = cn.prepareStatement(sql)) {
                    ps.setString(1, receiptNo);
                    ps.setInt(2, billId);
                    ps.setBigDecimal(3, amount);
                    ps.setString(4, method);
                    ps.setString(5, text(body, "cardType"));
                    ps.setString(6, text(body, "cardProvider"));
                    ps.setString(7, masked);
                    ps.setString(8, text(body, "bankName"));
                    ps.setString(9, text(body, "reference"));
                    ps.setInt(10, actor.getId());
                    ps.executeUpdate();
                }

                // The bill is marked PAID by trg_payment_after_insert.
                if (appointmentId != null) {
                    try (PreparedStatement ps = cn.prepareStatement(
                            "UPDATE appointments SET status = 'PAID', receipt_no = ? " +
                            "WHERE id = ? AND status = 'BOOKED'")) {
                        ps.setString(1, receiptNo);
                        ps.setInt(2, appointmentId);
                        ps.executeUpdate();
                    }
                }

                cn.commit();
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("receiptNo", receiptNo);
                result.put("invoiceNo", invoiceNo);
                result.put("cardNumberMasked", masked);
                return result;
            } catch (SQLException | RuntimeException e) {
                rollback(cn);
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not record the payment", e);
        }
    }

    private int consultationBill(Connection cn, int appointmentId, BigDecimal amount, User actor)
            throws SQLException {
        int patientId;
        int doctorId;
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT a.patient_id, s.doctor_id FROM appointments a " +
                "JOIN doctor_sessions s ON s.id = a.session_id WHERE a.id = ?")) {
            ps.setInt(1, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new NotFoundException("That appointment no longer exists");
                }
                patientId = rs.getInt(1);
                doctorId = rs.getInt(2);
            }
        }

        String billNo = IdGenerator.billNo(billPrefix(cn), nextBillSequence(cn));
        int billId;
        String sql = "INSERT INTO bills (bill_no, appointment_id, patient_id, doctor_id, consultation_fee, " +
                     "treatment_total, discount, tax, total, pricing_strategy, status, generated_by) " +
                     "VALUES (?,?,?,?,?,0,0,0,?, 'STANDARD', 'PENDING', ?)";
        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, billNo);
            ps.setInt(2, appointmentId);
            ps.setInt(3, patientId);
            ps.setInt(4, doctorId);
            ps.setBigDecimal(5, amount);
            ps.setBigDecimal(6, amount);
            ps.setInt(7, actor.getId());
            ps.executeUpdate();
            billId = generatedKey(ps);
        }

        try (PreparedStatement ps = cn.prepareStatement(
                "INSERT INTO bill_items (bill_id, description, quantity, unit_price, line_total) " +
                "VALUES (?, 'Doctor Consultation Fee', 1, ?, ?)")) {
            ps.setInt(1, billId);
            ps.setBigDecimal(2, amount);
            ps.setBigDecimal(3, amount);
            ps.executeUpdate();
        }
        return billId;
    }

    // =================================================================
    // Diagnostic reports, doctor pricing, settings
    // =================================================================

    public String addReport(Map<String, Object> body, User actor) {
        String nic = required(body, "patientNic", "Patient NIC");
        try (Connection cn = DBConnection.getConnection()) {
            Integer patientId = patientIdByNic(cn, nic);
            if (patientId == null) {
                throw new NotFoundException("No patient is registered with NIC " + nic);
            }
            int doctorId = actor.getRole() == Role.DOCTOR
                    ? actor.getId()
                    : PosIds.numeric(required(body, "doctorId", "Doctor"), "Doctor id");

            String reportType = textOr(body, "reportType", "Clinical Report");
            String reportDate = textOr(body, "date", LocalDate.now().toString());
            String fileName = reportDate + "_" + nic.replaceAll("[^A-Za-z0-9]", "") + "_"
                    + reportType.replaceAll("[^A-Za-z0-9]", "_") + ".pdf";

            String sql = "INSERT INTO medical_reports (report_no, patient_id, title, report_type, " +
                         "doctor_id, report_date, findings, status, file_name, uploaded_by) " +
                         "VALUES (?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                String reportNo = "RPT-" + (8900 + nextReportSequence(cn));
                ps.setString(1, reportNo);
                ps.setInt(2, patientId);
                ps.setString(3, reportType);
                ps.setString(4, reportType);
                ps.setInt(5, doctorId);
                ps.setString(6, reportDate);
                ps.setString(7, text(body, "findings"));
                ps.setString(8, textOr(body, "status", "VERIFIED"));
                ps.setString(9, fileName);
                ps.setInt(10, actor.getId());
                ps.executeUpdate();
                return PosIds.report(generatedKey(ps));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the report", e);
        }
    }

    public void setDoctorFee(Map<String, Object> body, User actor) {
        int doctorId = actor.getRole() == Role.DOCTOR
                ? actor.getId()
                : PosIds.numeric(required(body, "doctorId", "Doctor"), "Doctor id");
        int treatmentId = PosIds.numeric(required(body, "treatmentId", "Treatment"), "Treatment id");
        BigDecimal fee = decimal(body, "customFee", null);
        if (fee == null || fee.signum() < 0) {
            throw new ValidationException("Enter a valid charge for this procedure");
        }

        update("INSERT INTO doctor_treatment_pricing (doctor_id, treatment_id, custom_fee, updated_by) " +
               "VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE custom_fee = VALUES(custom_fee), " +
               "updated_by = VALUES(updated_by)",
               "Could not save the procedure charge",
               ps -> {
                   ps.setInt(1, doctorId);
                   ps.setInt(2, treatmentId);
                   ps.setBigDecimal(3, fee);
                   ps.setInt(4, actor.getId());
               });
    }

    public void updateSettings(Map<String, Object> body, User actor) {
        Map<String, String> keys = new LinkedHashMap<>();
        keys.put("clinicName", "clinic.name");
        keys.put("tagline", "clinic.tagline");
        keys.put("logoUrl", "clinic.logo");
        keys.put("iconUrl", "clinic.icon");
        keys.put("phone", "clinic.phone");
        keys.put("email", "clinic.email");
        keys.put("address", "clinic.address");
        keys.put("regNo", "clinic.reg.no");
        keys.put("currencySymbol", "billing.currency.symbol");
        keys.put("taxRate", "billing.tax.percent");
        keys.put("invoicePrefix", "billing.bill.prefix");
        keys.put("receiptPrefix", "billing.receipt.prefix");
        keys.put("footerNote", "clinic.footer.note");

        try (Connection cn = DBConnection.getConnection()) {
            String sql = "INSERT INTO settings (setting_key, setting_value, updated_by) VALUES (?,?,?) " +
                         "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), " +
                         "updated_by = VALUES(updated_by)";
            try (PreparedStatement ps = cn.prepareStatement(sql)) {
                for (Map.Entry<String, String> entry : keys.entrySet()) {
                    if (!body.containsKey(entry.getKey())) {
                        continue;
                    }
                    ps.setString(1, entry.getValue());
                    ps.setString(2, str(body.get(entry.getKey())));
                    ps.setInt(3, actor.getId());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the clinic settings", e);
        }
    }

    // =================================================================
    // Small shared helpers
    // =================================================================

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private void update(String sql, String failureMessage, Binder binder) {
        try (Connection cn = DBConnection.getConnection();
             PreparedStatement ps = cn.prepareStatement(sql)) {
            binder.bind(ps);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException(failureMessage, e);
        }
    }

    private Integer patientIdByNic(Connection cn, String nic) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("SELECT id FROM patients WHERE nic = ?")) {
            ps.setString(1, nic);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private Integer billIdByNo(Connection cn, String billNo) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("SELECT id FROM bills WHERE bill_no = ?")) {
            ps.setString(1, billNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private boolean usernameTaken(Connection cn, String username, int exceptId) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("SELECT id FROM users WHERE username = ? AND id <> ?")) {
            ps.setString(1, username);
            ps.setInt(2, exceptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private BigDecimal doctorFee(Connection cn, int doctorId) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT consultation_fee FROM doctor_profiles WHERE user_id = ?")) {
            ps.setInt(1, doctorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : new BigDecimal("2500");
            }
        }
    }

    private String doctorRoom(Connection cn, int doctorId) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT room_no FROM doctor_profiles WHERE user_id = ?")) {
            ps.setInt(1, doctorId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String billPrefix(Connection cn) throws SQLException {
        return setting(cn, "billing.bill.prefix", "SRD-INV-");
    }

    private String receiptPrefix(Connection cn) throws SQLException {
        return setting(cn, "billing.receipt.prefix", "SRD-REC-");
    }

    private String setting(Connection cn, String key, String fallback) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(
                "SELECT setting_value FROM settings WHERE setting_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getString(1) != null ? rs.getString(1) : fallback;
            }
        }
    }

    private int nextPatientSequence(Connection cn) throws SQLException {
        return count(cn, "SELECT COUNT(*) FROM patients") + 1;
    }

    private int nextAppointmentSequence(Connection cn) throws SQLException {
        return count(cn, "SELECT COUNT(*) FROM appointments WHERE DATE(created_at) = CURDATE()") + 1;
    }

    private int nextBillSequence(Connection cn) throws SQLException {
        return count(cn, "SELECT COUNT(*) FROM bills") + 1;
    }

    private int nextPaymentSequence(Connection cn) throws SQLException {
        return count(cn, "SELECT COUNT(*) FROM payments") + 1;
    }

    private int nextReportSequence(Connection cn) throws SQLException {
        return count(cn, "SELECT COUNT(*) FROM medical_reports") + 1;
    }

    private int count(Connection cn, String sql) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private int generatedKey(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getInt(1);
            }
        }
        throw new SQLException("The database did not return the new record's id");
    }

    private void rollback(Connection cn) {
        try {
            cn.rollback();
        } catch (SQLException ignored) {
            // nothing useful can be done here; the original failure is rethrown
        }
    }

    static String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return null;
        }
        String digits = cardNumber.replaceAll("\\s+", "");
        if (digits.length() >= 8) {
            return digits.substring(0, 4) + "-XXXX-XXXX-" + digits.substring(digits.length() - 4);
        }
        return "XXXX-XXXX-XXXX-" + digits;
    }

    private void setDate(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.DATE);
        } else {
            ps.setDate(index, Date.valueOf(value));
        }
    }

    private String gender(String value) {
        if (value == null) {
            return "OTHER";
        }
        String upper = value.trim().toUpperCase();
        return upper.equals("MALE") || upper.equals("FEMALE") ? upper : "OTHER";
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String textOr(Map<String, Object> body, String key, String fallback) {
        String value = text(body, key);
        return value == null ? fallback : value;
    }

    private static String required(Map<String, Object> body, String key, String label) {
        String value = text(body, key);
        if (value == null) {
            throw new ValidationException(label + " is required");
        }
        return value;
    }

    private static int intOr(Map<String, Object> body, String key, int fallback) {
        Object value = body.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static BigDecimal decimal(Map<String, Object> body, String key, BigDecimal fallback) {
        Object value = body.get(key);
        if (value == null) {
            return fallback;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim()).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new ValidationException(key + " must be an amount");
        }
    }
}
