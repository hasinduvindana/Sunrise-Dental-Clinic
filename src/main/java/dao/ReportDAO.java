package dao;

import exception.DataAccessException;
import util.DBConnection;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reporting queries. The heavy aggregations live in stored procedures
 * (sp_income_report, sp_doctor_income, sp_patient_report) so the reporting
 * logic sits next to the data instead of being re-implemented in Java.
 */
public class ReportDAO {

    public List<Map<String, Object>> incomeReport(String from, String to) {
        return callProcedure("{call sp_income_report(?,?)}", from, to, null);
    }

    public List<Map<String, Object>> doctorIncome(int doctorId, String from, String to) {
        try (Connection con = DBConnection.getConnection();
             CallableStatement cs = con.prepareCall("{call sp_doctor_income(?,?,?)}")) {
            cs.setInt(1, doctorId);
            cs.setDate(2, java.sql.Date.valueOf(from));
            cs.setDate(3, java.sql.Date.valueOf(to));
            try (ResultSet rs = cs.executeQuery()) {
                return rows(rs);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not build the doctor income report", e);
        }
    }

    public List<Map<String, Object>> patientReport(String from, String to) {
        return callProcedure("{call sp_patient_report(?,?)}", from, to, null);
    }

    /** Headline numbers for the dashboard cards. */
    public Map<String, Object> dashboardSummary() {
        String sql = "SELECT "
                   + " (SELECT COUNT(*) FROM patients) AS total_patients, "
                   + " (SELECT COUNT(*) FROM users WHERE role='DOCTOR'  AND status='ACTIVE') AS total_doctors, "
                   + " (SELECT COUNT(*) FROM users WHERE role='NURSE'   AND status='ACTIVE') AS total_nurses, "
                   + " (SELECT COUNT(*) FROM users WHERE role='CASHIER' AND status='ACTIVE') AS total_cashiers, "
                   + " (SELECT COUNT(*) FROM users WHERE role='ADMIN'   AND status='ACTIVE') AS total_admins, "
                   + " (SELECT COUNT(*) FROM appointments a JOIN doctor_sessions s ON s.id=a.session_id "
                   + "   WHERE s.session_date=CURDATE() AND a.status<>'CANCELLED') AS appointments_today, "
                   + " (SELECT COUNT(*) FROM doctor_sessions WHERE session_date=CURDATE()) AS sessions_today, "
                   + " (SELECT COALESCE(SUM(total),0) FROM bills WHERE DATE(created_at)=CURDATE() AND status='PAID') AS income_today, "
                   + " (SELECT COALESCE(SUM(total),0) FROM bills WHERE status='PENDING') AS outstanding, "
                   + " (SELECT COALESCE(SUM(total),0) FROM bills WHERE status='PAID' "
                   + "   AND MONTH(created_at)=MONTH(CURDATE()) AND YEAR(created_at)=YEAR(CURDATE())) AS income_month";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> rows = rows(rs);
            return rows.isEmpty() ? new LinkedHashMap<>() : rows.get(0);
        } catch (SQLException e) {
            throw new DataAccessException("Could not build the dashboard summary", e);
        }
    }

    /** Income per day for the last n days - feeds the dashboard chart. */
    public List<Map<String, Object>> incomeTrend(int days) {
        String sql = "SELECT DATE(created_at) AS income_date, COALESCE(SUM(total),0) AS total "
                   + "FROM bills WHERE status='PAID' AND created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY) "
                   + "GROUP BY DATE(created_at) ORDER BY income_date";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                return rows(rs);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not build the income trend", e);
        }
    }

    /** Which treatments earn the most - used by the admin treatment report. */
    public List<Map<String, Object>> treatmentBreakdown(String from, String to) {
        String sql = "SELECT t.name AS treatment, COUNT(*) AS times_used, COALESCE(SUM(bi.line_total),0) AS revenue "
                   + "FROM bill_items bi "
                   + "JOIN bills b ON b.id = bi.bill_id AND b.status <> 'CANCELLED' "
                   + "JOIN treatments t ON t.id = bi.treatment_id "
                   + "WHERE DATE(b.created_at) BETWEEN ? AND ? "
                   + "GROUP BY t.name ORDER BY revenue DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(from));
            ps.setDate(2, java.sql.Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                return rows(rs);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not build the treatment report", e);
        }
    }

    /** Per-doctor totals for the admin income report. */
    public List<Map<String, Object>> incomeByDoctor(String from, String to) {
        String sql = "SELECT u.id AS doctor_id, u.full_name AS doctor, COUNT(*) AS bill_count, "
                   + "COALESCE(SUM(b.total),0) AS revenue, "
                   + "COALESCE(SUM(CASE WHEN b.status='PAID' THEN b.total ELSE 0 END),0) AS collected "
                   + "FROM bills b JOIN users u ON u.id = b.doctor_id "
                   + "WHERE b.status <> 'CANCELLED' AND DATE(b.created_at) BETWEEN ? AND ? "
                   + "GROUP BY u.id, u.full_name ORDER BY revenue DESC";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, java.sql.Date.valueOf(from));
            ps.setDate(2, java.sql.Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                return rows(rs);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not build the doctor income summary", e);
        }
    }

    // ------------------------------------------------------------------

    private List<Map<String, Object>> callProcedure(String call, String from, String to, Integer extra) {
        try (Connection con = DBConnection.getConnection();
             CallableStatement cs = con.prepareCall(call)) {
            cs.setDate(1, java.sql.Date.valueOf(from));
            cs.setDate(2, java.sql.Date.valueOf(to));
            if (extra != null) {
                cs.setInt(3, extra);
            }
            try (ResultSet rs = cs.executeQuery()) {
                return rows(rs);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not run the report", e);
        }
    }

    /** Generic ResultSet to List<Map> conversion using the column labels. */
    private List<Map<String, Object>> rows(ResultSet rs) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int columns = meta.getColumnCount();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columns; i++) {
                Object value = rs.getObject(i);
                if (value instanceof java.sql.Date || value instanceof java.sql.Timestamp) {
                    value = String.valueOf(value);
                }
                row.put(camel(meta.getColumnLabel(i)), value);
            }
            list.add(row);
        }
        return list;
    }

    private String camel(String columnLabel) {
        String[] parts = columnLabel.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }
}
