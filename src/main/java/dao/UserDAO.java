package dao;

import exception.DataAccessException;
import model.Role;
import model.User;
import util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data access for login accounts and the doctor profile that hangs off them.
 * DESIGN PATTERN: Data Access Object - all SQL for users lives here and
 * nowhere else.
 */
public class UserDAO {

    private static final String BASE_SELECT =
            "SELECT u.*, d.specialization, d.qualification, d.consultation_fee, d.room_no "
          + "FROM users u LEFT JOIN doctor_profiles d ON d.user_id = u.id ";

    public User findById(int id) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(BASE_SELECT + "WHERE u.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load user " + id, e);
        }
    }

    public User findByUsername(String username) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(BASE_SELECT + "WHERE u.username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? map(rs) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load user " + username, e);
        }
    }

    public boolean usernameExists(String username) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not check username", e);
        }
    }

    public List<User> findByRole(Role role) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(BASE_SELECT + "WHERE u.role = ? ORDER BY u.full_name")) {
            ps.setString(1, role.name());
            return readAll(ps);
        } catch (SQLException e) {
            throw new DataAccessException("Could not list " + role, e);
        }
    }

    /** Every staff account, optionally filtered by role and search term. */
    public List<User> search(Role role, String term) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + "WHERE u.role <> 'PATIENT' ");
        List<Object> params = new ArrayList<>();
        if (role != null) {
            sql.append("AND u.role = ? ");
            params.add(role.name());
        }
        if (term != null && !term.isBlank()) {
            sql.append("AND (u.full_name LIKE ? OR u.username LIKE ? OR u.email LIKE ?) ");
            String like = "%" + term.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        sql.append("ORDER BY u.role, u.full_name");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return readAll(ps);
        } catch (SQLException e) {
            throw new DataAccessException("Could not search staff", e);
        }
    }

    public int countByRole(Role role) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM users WHERE role = ?")) {
            ps.setString(1, role.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not count " + role, e);
        }
    }

    /** Inserts the account and, for a doctor, the matching profile row. */
    public int insert(User user) {
        String sql = "INSERT INTO users (username, password_hash, salt, role, full_name, email, phone, status, created_by) "
                   + "VALUES (?,?,?,?,?,?,?,?,?)";
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            int newId;
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, user.getUsername());
                ps.setString(2, user.getPasswordHash());
                ps.setString(3, user.getSalt());
                ps.setString(4, user.getRole().name());
                ps.setString(5, user.getFullName());
                ps.setString(6, user.getEmail());
                ps.setString(7, user.getPhone());
                ps.setString(8, user.getStatus());
                if (user.getCreatedBy() == null) {
                    ps.setNull(9, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(9, user.getCreatedBy());
                }
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        con.rollback();
                        throw new DataAccessException("Account was not created", null);
                    }
                    newId = keys.getInt(1);
                }
            }

            if (user.getRole() == Role.DOCTOR) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO doctor_profiles (user_id, specialization, qualification, consultation_fee, room_no) "
                      + "VALUES (?,?,?,?,?)")) {
                    ps.setInt(1, newId);
                    ps.setString(2, user.getSpecialization() == null ? "General Dentistry" : user.getSpecialization());
                    ps.setString(3, user.getQualification());
                    ps.setBigDecimal(4, user.getConsultationFee() == null
                            ? new BigDecimal("1500.00") : user.getConsultationFee());
                    ps.setString(5, user.getRoomNo());
                    ps.executeUpdate();
                }
            }

            con.commit();
            user.setId(newId);
            return newId;
        } catch (SQLException e) {
            rollback(con);
            throw new DataAccessException("Could not create the account", e);
        } finally {
            close(con);
        }
    }

    public void update(User user) {
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE users SET full_name=?, email=?, phone=?, status=? WHERE id=?")) {
                ps.setString(1, user.getFullName());
                ps.setString(2, user.getEmail());
                ps.setString(3, user.getPhone());
                ps.setString(4, user.getStatus());
                ps.setInt(5, user.getId());
                ps.executeUpdate();
            }

            if (user.getRole() == Role.DOCTOR) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO doctor_profiles (user_id, specialization, qualification, consultation_fee, room_no) "
                      + "VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE "
                      + "specialization=VALUES(specialization), qualification=VALUES(qualification), "
                      + "consultation_fee=VALUES(consultation_fee), room_no=VALUES(room_no)")) {
                    ps.setInt(1, user.getId());
                    ps.setString(2, user.getSpecialization());
                    ps.setString(3, user.getQualification());
                    ps.setBigDecimal(4, user.getConsultationFee());
                    ps.setString(5, user.getRoomNo());
                    ps.executeUpdate();
                }
            }

            con.commit();
        } catch (SQLException e) {
            rollback(con);
            throw new DataAccessException("Could not update the account", e);
        } finally {
            close(con);
        }
    }

    public void updatePassword(int userId, String hash, String salt) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE users SET password_hash=?, salt=? WHERE id=?")) {
            ps.setString(1, hash);
            ps.setString(2, salt);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not change the password", e);
        }
    }

    public void updateStatus(int userId, String status) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE users SET status=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setInt(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not change the account status", e);
        }
    }

    public void delete(int userId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM users WHERE id=?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not delete the account", e);
        }
    }

    // ------------------------------------------------------------------

    private List<User> readAll(PreparedStatement ps) throws SQLException {
        List<User> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(map(rs));
            }
        }
        return list;
    }

    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setUsername(rs.getString("username"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setSalt(rs.getString("salt"));
        u.setRole(Role.of(rs.getString("role")));
        u.setFullName(rs.getString("full_name"));
        u.setEmail(rs.getString("email"));
        u.setPhone(rs.getString("phone"));
        u.setStatus(rs.getString("status"));
        int createdBy = rs.getInt("created_by");
        u.setCreatedBy(rs.wasNull() ? null : createdBy);
        u.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        u.setSpecialization(rs.getString("specialization"));
        u.setQualification(rs.getString("qualification"));
        u.setConsultationFee(rs.getBigDecimal("consultation_fee"));
        u.setRoomNo(rs.getString("room_no"));
        return u;
    }

    static void rollback(Connection con) {
        if (con != null) {
            try {
                con.rollback();
            } catch (SQLException ignored) {
                // nothing useful to do while already handling a failure
            }
        }
    }

    static void close(Connection con) {
        if (con != null) {
            try {
                con.setAutoCommit(true);
                con.close();
            } catch (SQLException ignored) {
                // connection is being discarded anyway
            }
        }
    }
}
