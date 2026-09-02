package listener;

import model.Role;
import util.DBConnection;
import util.PasswordUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

/**
 * Creates the staff accounts, doctor sessions and sample patients the clinic
 * starts with.
 *
 * This runs in Java rather than in the SQL seed script because passwords must
 * be hashed with the same salt-and-iterate routine the sign-in code uses; a
 * hash pasted into a .sql file would stop matching the moment that routine
 * changed. It only ever runs when the users table is empty apart from the
 * super admin, so restarting Tomcat never overwrites real clinic data.
 */
final class DemoDataSeeder {

    private DemoDataSeeder() {
    }

    /** Every seeded account starts with this password and must change it. */
    static final String DEFAULT_PASSWORD = "Sunrise@123";

    static void seed() {
        try (Connection cn = DBConnection.getConnection()) {
            if (staffCount(cn) > 1) {
                return;   // the clinic is already set up
            }

            int admin = user(cn, "admin", Role.ADMIN, "Chamari Gunawardena",
                    "admin@sunrisedental.lk", "+94 77 900 2222");

            int kasun = doctor(cn, "drkasun", "Dr. Kasun Silva", "dr.kasun@sunrisedental.lk",
                    "+94 77 900 3333", "Orthodontics & Cosmetic Dentistry", "BDS, MSc (Ortho)",
                    "2500", "Room 01");
            int amali = doctor(cn, "dramali", "Dr. Amali Fernando", "dr.amali@sunrisedental.lk",
                    "+94 77 900 4444", "Oral & Maxillofacial Surgeon", "BDS, MS (OMFS)",
                    "3000", "Room 02");
            int nimal = doctor(cn, "drnimal", "Dr. Nimal Perera", "dr.nimal@sunrisedental.lk",
                    "+94 77 900 5555", "Implantologist & Periodontist", "BDS, MSc (Implantology)",
                    "3500", "Room 03");
            int kavindi = doctor(cn, "drkavindi", "Dr. Kavindi Weerasinghe", "dr.kavindi@sunrisedental.lk",
                    "+94 77 900 6666", "Pediatric & Restorative Dentistry", "BDS, MSc (Paed)",
                    "2000", "Room 04");

            user(cn, "nurse1", Role.NURSE, "Sister Kumari Jayasinghe",
                    "kumari@sunrisedental.lk", "+94 77 900 7777");
            user(cn, "nurse2", Role.NURSE, "Nurse Dilani Rajapakse",
                    "dilani@sunrisedental.lk", "+94 77 900 8888");
            user(cn, "patadmin", Role.PATIENT_ADMIN, "Sahan Wickramasinghe",
                    "sahan@sunrisedental.lk", "+94 77 900 9999");
            user(cn, "cashier", Role.CASHIER, "Piyumi Wijesuriya",
                    "cashier@sunrisedental.lk", "+94 77 900 0000");

            patient(cn, "200012345678", "PAT-2026-0001", "Kavindu Sandeepana", "2000-04-12", "MALE",
                    "0714567890", "kavindu@gmail.com", "45/2, Temple Road, Mount Lavinia",
                    "O+", "Penicillin", "Mild asthmatic, no hypertension", admin);
            patient(cn, "199587654321", "PAT-2026-0002", "Anushka Thilakarathne", "1995-11-20", "FEMALE",
                    "0778901234", "anushka@outlook.com", "12B, Station Road, Dehiwala",
                    "B+", "None", "Orthodontic treatment completed in 2024", admin);
            patient(cn, "198834567890", "PAT-2026-0003", "Sunil Wickramatunga", "1988-06-05", "MALE",
                    "0751234567", "sunil.wick@gmail.com", "78/A, Kandy Road, Kiribathgoda",
                    "A+", "Aspirin", "Under regular review", admin);

            LocalDate today = LocalDate.now();
            session(cn, kasun, today.toString(), "09:00", "13:00", "Room 01", 12, "2500");
            session(cn, amali, today.toString(), "14:00", "18:00", "Room 02", 10, "3000");
            session(cn, nimal, today.plusDays(1).toString(), "09:30", "13:30", "Room 03", 8, "3500");
            session(cn, kavindi, today.plusDays(2).toString(), "10:00", "14:00", "Room 04", 15, "2000");
            session(cn, kasun, today.plusDays(3).toString(), "15:00", "19:00", "Room 01", 12, "2500");

            System.out.println("[Sunrise] seeded the starting staff, patients and sessions. "
                    + "Every seeded account signs in with the password " + DEFAULT_PASSWORD);
        } catch (SQLException e) {
            System.err.println("[Sunrise] could not seed the starting data: " + e.getMessage());
        }
    }

    private static int staffCount(Connection cn) throws SQLException {
        try (PreparedStatement ps = cn.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static int user(Connection cn, String username, Role role, String fullName,
                            String email, String phone) throws SQLException {
        String salt = PasswordUtil.newSalt();
        String sql = "INSERT INTO users (username, password_hash, salt, role, full_name, email, phone) " +
                     "VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = cn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, PasswordUtil.hash(DEFAULT_PASSWORD, salt));
            ps.setString(3, salt);
            ps.setString(4, role.name());
            ps.setString(5, fullName);
            ps.setString(6, email);
            ps.setString(7, phone);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        }
    }

    private static int doctor(Connection cn, String username, String fullName, String email,
                              String phone, String specialty, String qualification,
                              String fee, String room) throws SQLException {
        int id = user(cn, username, Role.DOCTOR, fullName, email, phone);
        String sql = "INSERT INTO doctor_profiles (user_id, specialization, qualification, " +
                     "consultation_fee, room_no) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, specialty);
            ps.setString(3, qualification);
            ps.setBigDecimal(4, new BigDecimal(fee));
            ps.setString(5, room);
            ps.executeUpdate();
        }
        return id;
    }

    private static void patient(Connection cn, String nic, String patientNo, String name, String dob,
                                String gender, String phone, String email, String address,
                                String bloodGroup, String allergies, String history, int registeredBy)
            throws SQLException {
        String sql = "INSERT INTO patients (patient_no, nic, full_name, date_of_birth, gender, contact, " +
                     "email, address, blood_group, allergies, medical_history, registered_by) " +
                     "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setString(1, patientNo);
            ps.setString(2, nic);
            ps.setString(3, name);
            ps.setString(4, dob);
            ps.setString(5, gender);
            ps.setString(6, phone);
            ps.setString(7, email);
            ps.setString(8, address);
            ps.setString(9, bloodGroup);
            ps.setString(10, allergies);
            ps.setString(11, history);
            ps.setInt(12, registeredBy);
            ps.executeUpdate();
        }
    }

    private static void session(Connection cn, int doctorId, String date, String start, String end,
                                String room, int maxPatients, String fee) throws SQLException {
        String sql = "INSERT INTO doctor_sessions (doctor_id, session_date, start_time, end_time, " +
                     "room_no, max_patients, consultation_fee, status) VALUES (?,?,?,?,?,?,?, 'ACTIVE')";
        try (PreparedStatement ps = cn.prepareStatement(sql)) {
            ps.setInt(1, doctorId);
            ps.setString(2, date);
            ps.setString(3, start);
            ps.setString(4, end);
            ps.setString(5, room);
            ps.setInt(6, maxPatients);
            ps.setBigDecimal(7, new BigDecimal(fee));
            ps.executeUpdate();
        }
    }
}
