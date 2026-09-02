package dao;

import exception.DataAccessException;
import model.Appointment;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for appointments. Reads go through the v_appointment_details
 * view so every screen sees the same joined shape.
 */
public class AppointmentDAO {

    private static final String VIEW_SELECT = "SELECT * FROM v_appointment_details ";

    public Appointment findById(int id) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(VIEW_SELECT + "WHERE appointment_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load appointment " + id, e);
        }
    }

    public Appointment findByNo(String appointmentNo) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(VIEW_SELECT + "WHERE appointment_no = ?")) {
            ps.setString(1, appointmentNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load appointment " + appointmentNo, e);
        }
    }

    public List<Appointment> findBySession(int sessionId) {
        return query(VIEW_SELECT + "WHERE session_id = ? ORDER BY queue_no", sessionId);
    }

    public List<Appointment> findByPatient(int patientId) {
        return query(VIEW_SELECT + "WHERE patient_id = ? ORDER BY session_date DESC, start_time DESC", patientId);
    }

    public List<Appointment> findByDoctor(int doctorId, String date) {
        String sql = VIEW_SELECT + "WHERE doctor_id = ? "
                   + (date == null || date.isBlank() ? "" : "AND session_date = ? ")
                   + "ORDER BY session_date DESC, queue_no";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            if (date != null && !date.isBlank()) {
                ps.setDate(2, java.sql.Date.valueOf(date));
            }
            return readAll(ps);
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the doctor's appointments", e);
        }
    }

    /** Front-desk list: everything on one date, any doctor. */
    public List<Appointment> findByDate(String date) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     VIEW_SELECT + "WHERE session_date = ? ORDER BY start_time, queue_no")) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            return readAll(ps);
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the day's appointments", e);
        }
    }

    public boolean patientAlreadyInSession(int sessionId, int patientId) {
        String sql = "SELECT 1 FROM appointments WHERE session_id=? AND patient_id=? AND status <> 'CANCELLED'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            ps.setInt(2, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not check the existing booking", e);
        }
    }

    public int countInSession(int sessionId) {
        String sql = "SELECT COUNT(*) FROM appointments WHERE session_id=? AND status <> 'CANCELLED'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not count the session bookings", e);
        }
    }

    public int countByDate(String date) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM appointments a JOIN doctor_sessions s ON s.id=a.session_id "
                   + "WHERE s.session_date = ? AND a.status <> 'CANCELLED'")) {
            ps.setDate(1, java.sql.Date.valueOf(date));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not count appointments", e);
        }
    }

    /**
     * Inserts the booking. queue_no is left at 0 on purpose - the
     * trg_appointment_before_insert trigger allocates the next number and
     * rejects the insert when the session is full, so the rule holds even if a
     * second client books at the same moment.
     */
    public int insert(Appointment a) {
        String sql = "INSERT INTO appointments (appointment_no, session_id, patient_id, treatment_id, notes, booked_by) "
                   + "VALUES (?,?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, a.getAppointmentNo());
            ps.setInt(2, a.getSessionId());
            ps.setInt(3, a.getPatientId());
            if (a.getTreatmentId() == null) {
                ps.setNull(4, Types.INTEGER);
            } else {
                ps.setInt(4, a.getTreatmentId());
            }
            ps.setString(5, a.getNotes());
            if (a.getBookedBy() == null) {
                ps.setNull(6, Types.INTEGER);
            } else {
                ps.setInt(6, a.getBookedBy());
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    a.setId(keys.getInt(1));
                    return a.getId();
                }
            }
            throw new DataAccessException("Appointment was not created", null);
        } catch (SQLException e) {
            // The trigger raises SQLSTATE 45000 with a readable message.
            throw new DataAccessException(cleanTriggerMessage(e), e);
        }
    }

    public void updateStatus(int appointmentId, String status) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE appointments SET status=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setInt(2, appointmentId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not change the appointment status", e);
        }
    }

    public void update(Appointment a) {
        String sql = "UPDATE appointments SET treatment_id=?, notes=?, status=? WHERE id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (a.getTreatmentId() == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, a.getTreatmentId());
            }
            ps.setString(2, a.getNotes());
            ps.setString(3, a.getStatus());
            ps.setInt(4, a.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the appointment", e);
        }
    }

    /** The patient the doctor should call next: lowest waiting queue number. */
    public Appointment findNextWaiting(int sessionId) {
        String sql = VIEW_SELECT + "WHERE session_id = ? AND status IN ('BOOKED','CHECKED_IN') "
                   + "ORDER BY queue_no LIMIT 1";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not find the next patient", e);
        }
    }

    // ------------------------------------------------------------------

    private List<Appointment> query(String sql, int param) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, param);
            return readAll(ps);
        } catch (SQLException e) {
            throw new DataAccessException("Could not load appointments", e);
        }
    }

    private List<Appointment> readAll(PreparedStatement ps) throws SQLException {
        List<Appointment> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    private String cleanTriggerMessage(SQLException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("Session is full")) {
            return "This session is already full - please choose another session";
        }
        if (message.contains("Session is closed")) {
            return "This session is closed and cannot take new bookings";
        }
        if (message.contains("uq_appt_patient_session")) {
            return "This patient already has a booking in that session";
        }
        return "Could not create the appointment";
    }

    private Appointment map(ResultSet rs) throws SQLException {
        Appointment a = new Appointment();
        a.setId(rs.getInt("appointment_id"));
        a.setAppointmentNo(rs.getString("appointment_no"));
        a.setQueueNo(rs.getInt("queue_no"));
        a.setStatus(rs.getString("status"));
        a.setNotes(rs.getString("notes"));
        int treatmentId = rs.getInt("treatment_id");
        a.setTreatmentId(rs.wasNull() ? null : treatmentId);
        a.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        a.setPatientId(rs.getInt("patient_id"));
        a.setPatientNo(rs.getString("patient_no"));
        a.setPatientName(rs.getString("patient_name"));
        a.setPatientContact(rs.getString("patient_contact"));
        a.setVip(rs.getBoolean("is_vip"));
        a.setSessionId(rs.getInt("session_id"));
        a.setSessionDate(String.valueOf(rs.getDate("session_date")));
        a.setStartTime(time(rs.getTime("start_time")));
        a.setEndTime(time(rs.getTime("end_time")));
        a.setRoomNo(rs.getString("room_no"));
        a.setCurrentQueueNo(rs.getInt("current_queue_no"));
        a.setConsultationFee(rs.getBigDecimal("consultation_fee"));
        a.setDoctorId(rs.getInt("doctor_id"));
        a.setDoctorName(rs.getString("doctor_name"));
        a.setTreatmentName(rs.getString("treatment_name"));
        a.setTreatmentPrice(rs.getBigDecimal("treatment_price"));
        return a;
    }

    private String time(java.sql.Time t) {
        return t == null ? null : t.toString().substring(0, 5);
    }
}
