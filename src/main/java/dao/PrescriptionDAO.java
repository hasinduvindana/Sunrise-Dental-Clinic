package dao;

import exception.DataAccessException;
import model.Prescription;
import model.PrescriptionItem;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Data access for prescriptions and their medicine lines. */
public class PrescriptionDAO {

    private static final String BASE_SELECT =
            "SELECT pr.*, u.full_name AS doctor_name, p.full_name AS patient_name, a.appointment_no "
          + "FROM prescriptions pr "
          + "JOIN users u        ON u.id = pr.doctor_id "
          + "JOIN patients p     ON p.id = pr.patient_id "
          + "JOIN appointments a ON a.id = pr.appointment_id ";

    public Prescription findById(int id) {
        return single(BASE_SELECT + "WHERE pr.id = ?", id);
    }

    public Prescription findByAppointment(int appointmentId) {
        return single(BASE_SELECT + "WHERE pr.appointment_id = ?", appointmentId);
    }

    public List<Prescription> findByPatient(int patientId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     BASE_SELECT + "WHERE pr.patient_id = ? ORDER BY pr.created_at DESC")) {
            ps.setInt(1, patientId);
            List<Prescription> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            for (Prescription p : list) {
                p.setItems(findItems(p.getId()));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load prescriptions", e);
        }
    }

    public int insert(Prescription prescription) {
        String sql = "INSERT INTO prescriptions (appointment_id, doctor_id, patient_id, diagnosis, advice) "
                   + "VALUES (?,?,?,?,?)";
        String itemSql = "INSERT INTO prescription_items (prescription_id, drug_name, dosage, frequency, "
                       + "duration_days, instructions) VALUES (?,?,?,?,?,?)";
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            int id;
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, prescription.getAppointmentId());
                ps.setInt(2, prescription.getDoctorId());
                ps.setInt(3, prescription.getPatientId());
                ps.setString(4, prescription.getDiagnosis());
                ps.setString(5, prescription.getAdvice());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        con.rollback();
                        throw new DataAccessException("Prescription was not created", null);
                    }
                    id = keys.getInt(1);
                }
            }

            try (PreparedStatement ps = con.prepareStatement(itemSql)) {
                for (PrescriptionItem item : prescription.getItems()) {
                    ps.setInt(1, id);
                    ps.setString(2, item.getDrugName());
                    ps.setString(3, item.getDosage());
                    ps.setString(4, item.getFrequency());
                    if (item.getDurationDays() == null) {
                        ps.setNull(5, Types.INTEGER);
                    } else {
                        ps.setInt(5, item.getDurationDays());
                    }
                    ps.setString(6, item.getInstructions());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            con.commit();
            prescription.setId(id);
            return id;
        } catch (SQLException e) {
            UserDAO.rollback(con);
            throw new DataAccessException("Could not save the prescription", e);
        } finally {
            UserDAO.close(con);
        }
    }

    public List<PrescriptionItem> findItems(int prescriptionId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT * FROM prescription_items WHERE prescription_id=? ORDER BY id")) {
            ps.setInt(1, prescriptionId);
            List<PrescriptionItem> items = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PrescriptionItem item = new PrescriptionItem();
                    item.setId(rs.getInt("id"));
                    item.setPrescriptionId(prescriptionId);
                    item.setDrugName(rs.getString("drug_name"));
                    item.setDosage(rs.getString("dosage"));
                    item.setFrequency(rs.getString("frequency"));
                    int days = rs.getInt("duration_days");
                    item.setDurationDays(rs.wasNull() ? null : days);
                    item.setInstructions(rs.getString("instructions"));
                    items.add(item);
                }
            }
            return items;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the prescription lines", e);
        }
    }

    private Prescription single(String sql, int param) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, param);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Prescription p = map(rs);
                p.setItems(findItems(p.getId()));
                return p;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the prescription", e);
        }
    }

    private Prescription map(ResultSet rs) throws SQLException {
        Prescription p = new Prescription();
        p.setId(rs.getInt("id"));
        p.setAppointmentId(rs.getInt("appointment_id"));
        p.setDoctorId(rs.getInt("doctor_id"));
        p.setPatientId(rs.getInt("patient_id"));
        p.setDiagnosis(rs.getString("diagnosis"));
        p.setAdvice(rs.getString("advice"));
        p.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        p.setDoctorName(rs.getString("doctor_name"));
        p.setPatientName(rs.getString("patient_name"));
        p.setAppointmentNo(rs.getString("appointment_no"));
        return p;
    }
}
