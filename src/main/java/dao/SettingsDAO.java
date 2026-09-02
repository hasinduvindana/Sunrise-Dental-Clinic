package dao;

import exception.DataAccessException;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.Map;

/** Key/value store behind the "General settings" screen. */
public class SettingsDAO {

    public Map<String, Object> findAll() {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT setting_key, setting_value FROM settings ORDER BY setting_key");
             ResultSet rs = ps.executeQuery()) {
            Map<String, Object> map = new LinkedHashMap<>();
            while (rs.next()) {
                map.put(rs.getString("setting_key"), rs.getString("setting_value"));
            }
            return map;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the settings", e);
        }
    }

    public String get(String key, String defaultValue) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT setting_value FROM settings WHERE setting_key=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String value = rs.getString(1);
                    return value == null || value.isBlank() ? defaultValue : value;
                }
                return defaultValue;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read setting " + key, e);
        }
    }

    public void put(String key, String value, Integer updatedBy) {
        String sql = "INSERT INTO settings (setting_key, setting_value, updated_by) VALUES (?,?,?) "
                   + "ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value), updated_by=VALUES(updated_by)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            if (updatedBy == null) {
                ps.setNull(3, Types.INTEGER);
            } else {
                ps.setInt(3, updatedBy);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save setting " + key, e);
        }
    }
}
