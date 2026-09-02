package model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A login account. Doctor-only attributes (specialisation, fee, room) live in
 * the doctor_profiles table and are loaded into the same object when the role
 * is DOCTOR, which keeps the API response for "staff" uniform.
 */
public class User {

    private int id;
    private String username;
    private String passwordHash;
    private String salt;
    private Role role;
    private String fullName;
    private String email;
    private String phone;
    private String status = "ACTIVE";
    private Integer createdBy;
    private String createdAt;

    // doctor profile (null for every other role)
    private String specialization;
    private String qualification;
    private BigDecimal consultationFee;
    private String roomNo;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Integer createdBy) { this.createdBy = createdBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }

    public String getQualification() { return qualification; }
    public void setQualification(String qualification) { this.qualification = qualification; }

    public BigDecimal getConsultationFee() { return consultationFee; }
    public void setConsultationFee(BigDecimal consultationFee) { this.consultationFee = consultationFee; }

    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }

    public boolean isActive() { return "ACTIVE".equalsIgnoreCase(status); }

    /** JSON projection. The hash and salt are never exposed to the client. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("username", username);
        map.put("role", role == null ? null : role.name());
        map.put("roleLabel", role == null ? null : role.label());
        map.put("fullName", fullName);
        map.put("email", email);
        map.put("phone", phone);
        map.put("status", status);
        map.put("createdAt", createdAt);
        if (role == Role.DOCTOR) {
            map.put("specialization", specialization);
            map.put("qualification", qualification);
            map.put("consultationFee", consultationFee);
            map.put("roomNo", roomNo);
        }
        return map;
    }
}
