package dao;

import exception.DataAccessException;
import model.Treatment;
import util.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Data access for the treatment price list. */
public class TreatmentDAO {

    public List<Treatment> findAll(boolean activeOnly) {
        String sql = "SELECT * FROM treatments " + (activeOnly ? "WHERE status='ACTIVE' " : "") + "ORDER BY name";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            List<Treatment> list = new ArrayList<>();
            while (rs.next()) {
                list.add(map(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load treatments", e);
        }
    }

    public Treatment findById(int id) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM treatments WHERE id=?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load treatment " + id, e);
        }
    }

    public boolean codeExists(String code, int excludeId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM treatments WHERE code=? AND id<>?")) {
            ps.setString(1, code);
            ps.setInt(2, excludeId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not check the treatment code", e);
        }
    }

    public int insert(Treatment t) {
        String sql = "INSERT INTO treatments (code, name, base_price, duration_minutes, status) VALUES (?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, t.getCode());
            ps.setString(2, t.getName());
            ps.setBigDecimal(3, t.getBasePrice());
            ps.setInt(4, t.getDurationMinutes());
            ps.setString(5, t.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    t.setId(keys.getInt(1));
                    return t.getId();
                }
            }
            throw new DataAccessException("Treatment was not created", null);
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the treatment", e);
        }
    }

    public void update(Treatment t) {
        String sql = "UPDATE treatments SET code=?, name=?, base_price=?, duration_minutes=?, status=? WHERE id=?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, t.getCode());
            ps.setString(2, t.getName());
            ps.setBigDecimal(3, t.getBasePrice());
            ps.setInt(4, t.getDurationMinutes());
            ps.setString(5, t.getStatus());
            ps.setInt(6, t.getId());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not update the treatment", e);
        }
    }

    public void delete(int id) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE treatments SET status='INACTIVE' WHERE id=?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not retire the treatment", e);
        }
    }

    private Treatment map(ResultSet rs) throws SQLException {
        Treatment t = new Treatment();
        t.setId(rs.getInt("id"));
        t.setCode(rs.getString("code"));
        t.setName(rs.getString("name"));
        t.setBasePrice(rs.getBigDecimal("base_price"));
        t.setDurationMinutes(rs.getInt("duration_minutes"));
        t.setStatus(rs.getString("status"));
        return t;
    }
}
