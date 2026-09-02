package dao;

import exception.DataAccessException;
import model.Bill;
import model.BillItem;
import model.Payment;
import util.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** Data access for bills, bill lines and payments. */
public class BillDAO {

    private static final String BASE_SELECT =
            "SELECT b.*, p.full_name AS patient_name, p.patient_no, u.full_name AS doctor_name, "
          + " a.appointment_no, "
          + " (SELECT COALESCE(SUM(pay.amount),0) FROM payments pay WHERE pay.bill_id = b.id) AS amount_paid "
          + "FROM bills b "
          + "JOIN patients p     ON p.id = b.patient_id "
          + "JOIN users u        ON u.id = b.doctor_id "
          + "JOIN appointments a ON a.id = b.appointment_id ";

    public Bill findById(int id) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(BASE_SELECT + "WHERE b.id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Bill bill = map(rs);
                bill.setItems(findItems(bill.getId()));
                return bill;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load bill " + id, e);
        }
    }

    public Bill findByAppointment(int appointmentId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(BASE_SELECT + "WHERE b.appointment_id = ?")) {
            ps.setInt(1, appointmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Bill bill = map(rs);
                bill.setItems(findItems(bill.getId()));
                return bill;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the bill for this appointment", e);
        }
    }

    public List<Bill> find(String status, String fromDate, String toDate, Integer patientId, Integer doctorId) {
        StringBuilder sql = new StringBuilder(BASE_SELECT + "WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append("AND b.status = ? ");
            params.add(status);
        }
        if (fromDate != null && !fromDate.isBlank()) {
            sql.append("AND DATE(b.created_at) >= ? ");
            params.add(java.sql.Date.valueOf(fromDate));
        }
        if (toDate != null && !toDate.isBlank()) {
            sql.append("AND DATE(b.created_at) <= ? ");
            params.add(java.sql.Date.valueOf(toDate));
        }
        if (patientId != null) {
            sql.append("AND b.patient_id = ? ");
            params.add(patientId);
        }
        if (doctorId != null) {
            sql.append("AND b.doctor_id = ? ");
            params.add(doctorId);
        }
        sql.append("ORDER BY b.created_at DESC LIMIT 500");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            List<Bill> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(map(rs));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load bills", e);
        }
    }

    public int nextSequence() {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COALESCE(MAX(id),0) + 1 FROM bills");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 1;
        } catch (SQLException e) {
            throw new DataAccessException("Could not allocate a bill number", e);
        }
    }

    /** Header and lines are written inside one transaction. */
    public int insert(Bill bill) {
        String billSql = "INSERT INTO bills (bill_no, appointment_id, patient_id, doctor_id, consultation_fee, "
                       + "treatment_total, discount, tax, total, pricing_strategy, status, generated_by) "
                       + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        String itemSql = "INSERT INTO bill_items (bill_id, treatment_id, description, quantity, unit_price, line_total) "
                       + "VALUES (?,?,?,?,?,?)";
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            int billId;
            try (PreparedStatement ps = con.prepareStatement(billSql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, bill.getBillNo());
                ps.setInt(2, bill.getAppointmentId());
                ps.setInt(3, bill.getPatientId());
                ps.setInt(4, bill.getDoctorId());
                ps.setBigDecimal(5, bill.getConsultationFee());
                ps.setBigDecimal(6, bill.getTreatmentTotal());
                ps.setBigDecimal(7, bill.getDiscount());
                ps.setBigDecimal(8, bill.getTax());
                ps.setBigDecimal(9, bill.getTotal());
                ps.setString(10, bill.getPricingStrategy());
                ps.setString(11, bill.getStatus());
                if (bill.getGeneratedBy() == null) {
                    ps.setNull(12, Types.INTEGER);
                } else {
                    ps.setInt(12, bill.getGeneratedBy());
                }
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        con.rollback();
                        throw new DataAccessException("Bill was not created", null);
                    }
                    billId = keys.getInt(1);
                }
            }

            try (PreparedStatement ps = con.prepareStatement(itemSql)) {
                for (BillItem item : bill.getItems()) {
                    ps.setInt(1, billId);
                    if (item.getTreatmentId() == null) {
                        ps.setNull(2, Types.INTEGER);
                    } else {
                        ps.setInt(2, item.getTreatmentId());
                    }
                    ps.setString(3, item.getDescription());
                    ps.setInt(4, item.getQuantity());
                    ps.setBigDecimal(5, item.getUnitPrice());
                    ps.setBigDecimal(6, item.getLineTotal());
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            con.commit();
            bill.setId(billId);
            return billId;
        } catch (SQLException e) {
            UserDAO.rollback(con);
            throw new DataAccessException("Could not save the bill", e);
        } finally {
            UserDAO.close(con);
        }
    }

    public List<BillItem> findItems(int billId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM bill_items WHERE bill_id=? ORDER BY id")) {
            ps.setInt(1, billId);
            List<BillItem> items = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BillItem item = new BillItem();
                    item.setId(rs.getInt("id"));
                    item.setBillId(billId);
                    int treatmentId = rs.getInt("treatment_id");
                    item.setTreatmentId(rs.wasNull() ? null : treatmentId);
                    item.setDescription(rs.getString("description"));
                    item.setQuantity(rs.getInt("quantity"));
                    item.setUnitPrice(rs.getBigDecimal("unit_price"));
                    item.setLineTotal(rs.getBigDecimal("line_total"));
                    items.add(item);
                }
            }
            return items;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the bill lines", e);
        }
    }

    public void updateStatus(int billId, String status) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE bills SET status=? WHERE id=?")) {
            ps.setString(1, status);
            ps.setInt(2, billId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not change the bill status", e);
        }
    }

    // ------------------------- payments -------------------------

    /** The trg_payment_after_insert trigger flips the bill to PAID when settled. */
    public int insertPayment(Payment payment) {
        String sql = "INSERT INTO payments (bill_id, amount, method, reference, received_by) VALUES (?,?,?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, payment.getBillId());
            ps.setBigDecimal(2, payment.getAmount());
            ps.setString(3, payment.getMethod());
            ps.setString(4, payment.getReference());
            if (payment.getReceivedBy() == null) {
                ps.setNull(5, Types.INTEGER);
            } else {
                ps.setInt(5, payment.getReceivedBy());
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    payment.setId(keys.getInt(1));
                    return payment.getId();
                }
            }
            throw new DataAccessException("Payment was not recorded", null);
        } catch (SQLException e) {
            throw new DataAccessException("Could not record the payment", e);
        }
    }

    public List<Payment> findPayments(int billId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM payments WHERE bill_id=? ORDER BY paid_at")) {
            ps.setInt(1, billId);
            List<Payment> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Payment p = new Payment();
                    p.setId(rs.getInt("id"));
                    p.setBillId(billId);
                    p.setAmount(rs.getBigDecimal("amount"));
                    p.setMethod(rs.getString("method"));
                    p.setReference(rs.getString("reference"));
                    int receivedBy = rs.getInt("received_by");
                    p.setReceivedBy(rs.wasNull() ? null : receivedBy);
                    p.setPaidAt(String.valueOf(rs.getTimestamp("paid_at")));
                    list.add(p);
                }
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the payments", e);
        }
    }

    public BigDecimal amountPaid(int billId) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COALESCE(SUM(amount),0) FROM payments WHERE bill_id=?")) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not total the payments", e);
        }
    }

    private Bill map(ResultSet rs) throws SQLException {
        Bill b = new Bill();
        b.setId(rs.getInt("id"));
        b.setBillNo(rs.getString("bill_no"));
        b.setAppointmentId(rs.getInt("appointment_id"));
        b.setAppointmentNo(rs.getString("appointment_no"));
        b.setPatientId(rs.getInt("patient_id"));
        b.setPatientNo(rs.getString("patient_no"));
        b.setPatientName(rs.getString("patient_name"));
        b.setDoctorId(rs.getInt("doctor_id"));
        b.setDoctorName(rs.getString("doctor_name"));
        b.setConsultationFee(rs.getBigDecimal("consultation_fee"));
        b.setTreatmentTotal(rs.getBigDecimal("treatment_total"));
        b.setDiscount(rs.getBigDecimal("discount"));
        b.setTax(rs.getBigDecimal("tax"));
        b.setTotal(rs.getBigDecimal("total"));
        b.setPricingStrategy(rs.getString("pricing_strategy"));
        b.setStatus(rs.getString("status"));
        int generatedBy = rs.getInt("generated_by");
        b.setGeneratedBy(rs.wasNull() ? null : generatedBy);
        b.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));
        java.sql.Timestamp paidAt = rs.getTimestamp("paid_at");
        b.setPaidAt(paidAt == null ? null : paidAt.toString());
        b.setAmountPaid(rs.getBigDecimal("amount_paid"));
        return b;
    }
}
