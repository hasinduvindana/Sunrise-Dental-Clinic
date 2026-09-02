package dao;

import exception.DataAccessException;
import model.MedicalReport;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Metadata access for the medical report files kept on the server disk. */
public class MedicalReportDAO {

    private static final String BASE_SELECT =
            "SELECT r.*, p.full_name AS patient_name, u.full_name AS uploaded_by_name "
          + "FROM medical_reports r "
          + "JOIN patients p    ON p.id = r.patient_id "
          + "LEFT JOIN users u  ON u.id = r.uploaded_by ";

    public MedicalReport findById(int id) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(BASE_SELECT + "WHERE r.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load report " + id, e);
        }
    }

    public List<MedicalReport> findByPatient(int patientId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     BASE_SELECT + "WHERE r.patient_id = ? ORDER BY r.created_at DESC")) {
            ps.setInt(1, patientId);
            List<MedicalReport> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the patient's reports", e);
        }
    }

    public int insert(MedicalReport report) {
        String sql = "INSERT INTO medical_reports (patient_id, appointment_id, title, file_name, file_path, "
                   + "content_type, uploaded_by) VALUES (?,?,?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, report.getPatientId());
            if (report.getAppointmentId() == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, report.getAppointmentId());
            }
            ps.setString(3, report.getTitle());
            ps.setString(4, report.getFileName());
            ps.setString(5, report.getFilePath());
            ps.setString(6, report.getContentType());
            if (report.getUploadedBy() == null) {
                ps.setNull(7, Types.INTEGER);
            } else {
                ps.setInt(7, report.getUploadedBy());
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    report.setId(keys.getInt(1));
                    return report.getId();
                }
            }
            throw new DataAccessException("Report was not saved", null);
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the report", e);
        }
    }

    public void delete(int id) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM medical_reports WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the report", e);
        }
    }

    private MedicalReport map(ResultSet rs) throws SQLException {
        MedicalReport r = new MedicalReport();
        r.setId(rs.getInt("id"));
        r.setPatientId(rs.getInt("patient_id"));
        int appointmentId = rs.getInt("appointment_id");
        r.setAppointmentId(rs.wasNull() ? null : appointmentId);
        r.setTitle(rs.getString("title"));
        r.setFileName(rs.getString("file_name"));
        r.setFilePath(rs.getString("file_path"));
        r.setContentType(rs.getString("content_type"));
        int uploadedBy = rs.getInt("uploaded_by");
        r.setUploadedBy(rs.wasNull() ? null : uploadedBy);
        r.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        r.setPatientName(rs.getString("patient_name"));
        r.setUploadedByName(rs.getString("uploaded_by_name"));
        return r;
    }
}
