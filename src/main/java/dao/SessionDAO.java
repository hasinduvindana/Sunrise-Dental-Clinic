package dao;

import exception.DataAccessException;
import model.DoctorSession;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Data access for doctor sessions, including live queue counters. */
public class SessionDAO {

    private static final String BASE_SELECT =
            "SELECT s.*, u.full_name AS doctor_name, "
          + " (SELECT COUNT(*) FROM appointments a WHERE a.session_id = s.id AND a.status <> 'CANCELLED') AS booked_count, "
          + " (SELECT COUNT(*) FROM appointments a WHERE a.session_id = s.id AND a.status IN ('BOOKED','CHECKED_IN')) AS waiting_count "
          + "FROM doctor_sessions s JOIN users u ON u.id = s.doctor_id ";

    public DoctorSession findById(int id) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(BASE_SELECT + "WHERE s.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load session " + id, e);
        }
    }

    /** Sessions filtered by doctor and/or date. Both filters are optional. */
    public List<DoctorSession> find(Integer doctorId, String date, String status) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (doctorId != null) {
            sql.append("AND s.doctor_id = ? ");
            params.add(doctorId);
        }
        if (date != null && !date.isBlank()) {
            sql.append("AND s.session_date = ? ");
            params.add(java.sql.Date.valueOf(date));
        }
        if (status != null && !status.isBlank()) {
            sql.append("AND s.status = ? ");
            params.add(status);
        }
        sql.append("ORDER BY s.session_date DESC, s.start_time");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            List<DoctorSession> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load sessions", e);
        }
    }

    /** Sessions a patient may still book: today or later, not closed, not full. */
    public List<DoctorSession> findBookable() {
        String sql = BASE_SELECT
                   + "WHERE s.session_date >= CURDATE() AND s.status IN ('SCHEDULED','ACTIVE') "
                   + "ORDER BY s.session_date, s.start_time";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<DoctorSession> list = new ArrayList<>();
            while (rs.next()) {
                DoctorSession s = map(rs);
                if (s.getAvailableSlots() > 0) {
                    list.add(s);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load available sessions", e);
        }
    }

    public boolean slotTaken(int doctorId, String date, String startTime, int excludeId) {
        String sql = "SELECT 1 FROM doctor_sessions WHERE doctor_id=? AND session_date=? AND start_time=? AND id<>?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setDate(2, java.sql.Date.valueOf(date));
            ps.setTime(3, java.sql.Time.valueOf(startTime + ":00"));
            ps.setInt(4, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not check the session slot", e);
        }
    }

    public int insert(DoctorSession s) {
        String sql = "INSERT INTO doctor_sessions (doctor_id, session_date, start_time, end_time, room_no, "
                   + "max_patients, consultation_fee, status) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, s.getDoctorId());
            ps.setDate(2, java.sql.Date.valueOf(s.getSessionDate()));
            ps.setTime(3, java.sql.Time.valueOf(s.getStartTime() + ":00"));
            ps.setTime(4, java.sql.Time.valueOf(s.getEndTime() + ":00"));
            ps.setString(5, s.getRoomNo());
            ps.setInt(6, s.getMaxPatients());
            ps.setBigDecimal(7, s.getConsultationFee());
            ps.setString(8, s.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    s.setId(keys.getInt(1));
                    return s.getId();
                }
            }
            throw new DataAccessException("Session was not created", null);
        } catch (SQLException e) {
            throw new DataAccessException("Could not create the session", e);
        }
    }

    public void update(DoctorSession s) {
        String sql = "UPDATE doctor_sessions SET session_date=?, start_time=?, end_time=?, room_no=?, "
                   + "max_patients=?, consultation_fee=?, status=? WHERE id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(s.getSessionDate()));
            ps.setTime(2, java.sql.Time.valueOf(s.getStartTime() + ":00"));
            ps.setTime(3, java.sql.Time.valueOf(s.getEndTime() + ":00"));
            ps.setString(4, s.getRoomNo());
            ps.setInt(5, s.getMaxPatients());
            ps.setBigDecimal(6, s.getConsultationFee());
            ps.setString(7, s.getStatus());
            ps.setInt(8, s.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the session", e);
        }
    }

    public void updateStatus(int sessionId, String status) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE doctor_sessions SET status=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not change the session status", e);
        }
    }

    public void updateCurrentQueueNo(int sessionId, int queueNo) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE doctor_sessions SET current_queue_no=? WHERE id=?")) {
            ps.setInt(1, queueNo);
            ps.setInt(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not move the queue forward", e);
        }
    }

    public void delete(int id) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM doctor_sessions WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the session", e);
        }
    }

    private DoctorSession map(ResultSet rs) throws SQLException {
        DoctorSession s = new DoctorSession();
        s.setId(rs.getInt("id"));
        s.setDoctorId(rs.getInt("doctor_id"));
        s.setDoctorName(rs.getString("doctor_name"));
        s.setSessionDate(String.valueOf(rs.getDate("session_date")));
        s.setStartTime(shortTime(rs.getTime("start_time")));
        s.setEndTime(shortTime(rs.getTime("end_time")));
        s.setRoomNo(rs.getString("room_no"));
        s.setMaxPatients(rs.getInt("max_patients"));
        s.setConsultationFee(rs.getBigDecimal("consultation_fee"));
        s.setCurrentQueueNo(rs.getInt("current_queue_no"));
        s.setStatus(rs.getString("status"));
        s.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        s.setBookedCount(rs.getInt("booked_count"));
        s.setWaitingCount(rs.getInt("waiting_count"));
        return s;
    }

    private String shortTime(java.sql.Time time) {
        return time == null ? null : time.toString().substring(0, 5);
    }
}
