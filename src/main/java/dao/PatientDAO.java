package dao;

import exception.DataAccessException;
import model.Patient;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Data access for patient records. */
public class PatientDAO {

    private static final String BASE_SELECT =
            "SELECT p.*, u.username FROM patients p LEFT JOIN users u ON u.id = p.user_id ";

    public Patient findById(int id) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(BASE_SELECT + "WHERE p.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load patient " + id, e);
        }
    }

    public Patient findByUserId(int userId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(BASE_SELECT + "WHERE p.user_id = ?")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the patient profile", e);
        }
    }

    public Patient findByPatientNo(String patientNo) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(BASE_SELECT + "WHERE p.patient_no = ?")) {
            ps.setString(1, patientNo);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load patient " + patientNo, e);
        }
    }

    public List<Patient> search(String term) {
        String sql = BASE_SELECT
                   + (term == null || term.isBlank()
                        ? "ORDER BY p.created_at DESC LIMIT 500"
                        : "WHERE p.full_name LIKE ? OR p.patient_no LIKE ? OR p.contact LIKE ? OR p.nic LIKE ? "
                        + "ORDER BY p.full_name LIMIT 500");
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (term != null && !term.isBlank()) {
                String like = "%" + term.trim() + "%";
                for (int i = 1; i <= 4; i++) {
                    ps.setString(i, like);
                }
            }
            List<Patient> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not search patients", e);
        }
    }

    public int countAll() {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM patients");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Could not count patients", e);
        }
    }

    /** Sequence used to build the next PAT-YYYY-nnnn reference. */
    public int nextSequence() {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COALESCE(MAX(id),0) + 1 FROM patients");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 1;
        } catch (SQLException e) {
            throw new DataAccessException("Could not allocate a patient number", e);
        }
    }

    public int insert(Patient p) {
        String sql = "INSERT INTO patients (patient_no, user_id, full_name, nic, date_of_birth, gender, address, "
                   + "contact, is_vip, notes, registered_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, p.getPatientNo());
            setNullableInt(ps, 2, p.getUserId());
            ps.setString(3, p.getFullName());
            ps.setString(4, p.getNic());
            if (p.getDateOfBirth() == null) {
                ps.setNull(5, Types.DATE);
            } else {
                ps.setDate(5, java.sql.Date.valueOf(p.getDateOfBirth()));
            }
            ps.setString(6, p.getGender() == null ? "OTHER" : p.getGender());
            ps.setString(7, p.getAddress());
            ps.setString(8, p.getContact());
            ps.setBoolean(9, p.isVip());
            ps.setString(10, p.getNotes());
            setNullableInt(ps, 11, p.getRegisteredBy());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    p.setId(keys.getInt(1));
                    return p.getId();
                }
            }
            throw new DataAccessException("Patient record was not created", null);
        } catch (SQLException e) {
            throw new DataAccessException("Could not register the patient", e);
        }
    }

    public void update(Patient p) {
        String sql = "UPDATE patients SET full_name=?, nic=?, date_of_birth=?, gender=?, address=?, contact=?, "
                   + "is_vip=?, notes=? WHERE id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, p.getFullName());
            ps.setString(2, p.getNic());
            if (p.getDateOfBirth() == null) {
                ps.setNull(3, Types.DATE);
            } else {
                ps.setDate(3, java.sql.Date.valueOf(p.getDateOfBirth()));
            }
            ps.setString(4, p.getGender());
            ps.setString(5, p.getAddress());
            ps.setString(6, p.getContact());
            ps.setBoolean(7, p.isVip());
            ps.setString(8, p.getNotes());
            ps.setInt(9, p.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the patient", e);
        }
    }

    public void linkUser(int patientId, int userId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE patients SET user_id=? WHERE id=?")) {
            ps.setInt(1, userId);
            ps.setInt(2, patientId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not attach the portal login", e);
        }
    }

    public void delete(int id) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM patients WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the patient", e);
        }
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private Patient map(ResultSet rs) throws SQLException {
        Patient p = new Patient();
        p.setId(rs.getInt("id"));
        p.setPatientNo(rs.getString("patient_no"));
        int userId = rs.getInt("user_id");
        p.setUserId(rs.wasNull() ? null : userId);
        p.setFullName(rs.getString("full_name"));
        p.setNic(rs.getString("nic"));
        java.sql.Date dob = rs.getDate("date_of_birth");
        p.setDateOfBirth(dob == null ? null : dob.toString());
        p.setGender(rs.getString("gender"));
        p.setAddress(rs.getString("address"));
        p.setContact(rs.getString("contact"));
        p.setVip(rs.getBoolean("is_vip"));
        p.setNotes(rs.getString("notes"));
        int regBy = rs.getInt("registered_by");
        p.setRegisteredBy(rs.wasNull() ? null : regBy);
        p.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        p.setUsername(rs.getString("username"));
        return p;
    }
}
