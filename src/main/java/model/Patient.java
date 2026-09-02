package model;

import java.util.LinkedHashMap;
import java.util.Map;

/** A patient record. user_id is set only when the patient has a portal login. */
public class Patient {

    private int id;
    private String patientNo;
    private Integer userId;
    private String fullName;
    private String nic;
    private String dateOfBirth;
    private String gender;
    private String address;
    private String contact;
    private boolean vip;
    private String notes;
    private Integer registeredBy;
    private String createdAt;
    private String username;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getPatientNo() { return patientNo; }
    public void setPatientNo(String patientNo) { this.patientNo = patientNo; }

    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getNic() { return nic; }
    public void setNic(String nic) { this.nic = nic; }

    public String getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(String dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public boolean isVip() { return vip; }
    public void setVip(boolean vip) { this.vip = vip; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Integer getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(Integer registeredBy) { this.registeredBy = registeredBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("patientNo", patientNo);
        map.put("userId", userId);
        map.put("username", username);
        map.put("fullName", fullName);
        map.put("nic", nic);
        map.put("dateOfBirth", dateOfBirth);
        map.put("gender", gender);
        map.put("address", address);
        map.put("contact", contact);
        map.put("vip", vip);
        map.put("notes", notes);
        map.put("createdAt", createdAt);
        return map;
    }
}
