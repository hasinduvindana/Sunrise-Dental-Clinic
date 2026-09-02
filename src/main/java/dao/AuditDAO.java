package dao;

import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Write-only trail of who did what. Failures here are logged and swallowed:
 * an audit problem must never break the clinical action the user just took.
 */
public class AuditDAO {

    public void log(Integer userId, String role, String action, String entity, String entityId, String details) {
        String sql = "INSERT INTO audit_log (user_id, role, action, entity, entity_id, details) VALUES (?,?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (userId == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, userId);
            }
            ps.setString(2, role);
            ps.setString(3, action);
            ps.setString(4, entity);
            ps.setString(5, entityId);
            ps.setString(6, details);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[AuditDAO] could not write audit entry: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> recent(int limit) {
        String sql = "SELECT a.*, u.full_name FROM audit_log a LEFT JOIN users u ON u.id = a.user_id "
                   + "ORDER BY a.created_at DESC LIMIT ?";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("user", rs.getString("full_name"));
                    row.put("role", rs.getString("role"));
                    row.put("action", rs.getString("action"));
                    row.put("entity", rs.getString("entity"));
                    row.put("entityId", rs.getString("entity_id"));
                    row.put("details", rs.getString("details"));
                    row.put("createdAt", String.valueOf(rs.getTimestamp("created_at")));
                    list.add(row);
                }
            }
        } catch (SQLException e) {
            System.err.println("[AuditDAO] could not read audit entries: " + e.getMessage());
        }
        return list;
    }
}
