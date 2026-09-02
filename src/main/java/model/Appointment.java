package model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** One patient's place inside one doctor session. */
public class Appointment {

    private int id;
    private String appointmentNo;
    private int sessionId;
    private int patientId;
    private Integer treatmentId;
    private int queueNo;
    private String status = "BOOKED";
    private String notes;
    private Integer bookedBy;
    private String createdAt;

    // joined display fields
    private String patientNo;
    private String patientName;
    private String patientContact;
    private boolean vip;
    private String sessionDate;
    private String startTime;
    private String endTime;
    private String roomNo;
    private int currentQueueNo;
    private BigDecimal consultationFee;
    private int doctorId;
    private String doctorName;
    private String treatmentName;
    private BigDecimal treatmentPrice;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getAppointmentNo() { return appointmentNo; }
    public void setAppointmentNo(String appointmentNo) { this.appointmentNo = appointmentNo; }

    public int getSessionId() { return sessionId; }
    public void setSessionId(int sessionId) { this.sessionId = sessionId; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public Integer getTreatmentId() { return treatmentId; }
    public void setTreatmentId(Integer treatmentId) { this.treatmentId = treatmentId; }

    public int getQueueNo() { return queueNo; }
    public void setQueueNo(int queueNo) { this.queueNo = queueNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Integer getBookedBy() { return bookedBy; }
    public void setBookedBy(Integer bookedBy) { this.bookedBy = bookedBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getPatientNo() { return patientNo; }
    public void setPatientNo(String patientNo) { this.patientNo = patientNo; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getPatientContact() { return patientContact; }
    public void setPatientContact(String patientContact) { this.patientContact = patientContact; }

    public boolean isVip() { return vip; }
    public void setVip(boolean vip) { this.vip = vip; }

    public String getSessionDate() { return sessionDate; }
    public void setSessionDate(String sessionDate) { this.sessionDate = sessionDate; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }

    public int getCurrentQueueNo() { return currentQueueNo; }
    public void setCurrentQueueNo(int currentQueueNo) { this.currentQueueNo = currentQueueNo; }

    public BigDecimal getConsultationFee() { return consultationFee; }
    public void setConsultationFee(BigDecimal consultationFee) { this.consultationFee = consultationFee; }

    public int getDoctorId() { return doctorId; }
    public void setDoctorId(int doctorId) { this.doctorId = doctorId; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getTreatmentName() { return treatmentName; }
    public void setTreatmentName(String treatmentName) { this.treatmentName = treatmentName; }

    public BigDecimal getTreatmentPrice() { return treatmentPrice; }
    public void setTreatmentPrice(BigDecimal treatmentPrice) { this.treatmentPrice = treatmentPrice; }

    /** How many patients are ahead of this one right now. */
    public int getPositionsAhead() {
        return Math.max(0, queueNo - currentQueueNo);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("appointmentNo", appointmentNo);
        map.put("sessionId", sessionId);
        map.put("patientId", patientId);
        map.put("patientNo", patientNo);
        map.put("patientName", patientName);
        map.put("patientContact", patientContact);
        map.put("vip", vip);
        map.put("treatmentId", treatmentId);
        map.put("treatmentName", treatmentName);
        map.put("treatmentPrice", treatmentPrice);
        map.put("queueNo", queueNo);
        map.put("currentQueueNo", currentQueueNo);
        map.put("positionsAhead", getPositionsAhead());
        map.put("status", status);
        map.put("notes", notes);
        map.put("sessionDate", sessionDate);
        map.put("startTime", startTime);
        map.put("endTime", endTime);
        map.put("roomNo", roomNo);
        map.put("consultationFee", consultationFee);
        map.put("doctorId", doctorId);
        map.put("doctorName", doctorName);
        map.put("createdAt", createdAt);
        return map;
    }
}
