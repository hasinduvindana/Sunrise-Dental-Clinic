package model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A bookable clinic slot owned by one doctor. current_queue_no is the number
 * the doctor is seeing right now - it is what the patient portal polls to show
 * "now serving".
 */
public class DoctorSession {

    private int id;
    private int doctorId;
    private String doctorName;
    private String sessionDate;
    private String startTime;
    private String endTime;
    private String roomNo;
    private int maxPatients = 20;
    private BigDecimal consultationFee;
    private int currentQueueNo;
    private String status = "SCHEDULED";
    private String createdAt;

    // derived, filled in by the DAO join
    private int bookedCount;
    private int waitingCount;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getDoctorId() { return doctorId; }
    public void setDoctorId(int doctorId) { this.doctorId = doctorId; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getSessionDate() { return sessionDate; }
    public void setSessionDate(String sessionDate) { this.sessionDate = sessionDate; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }

    public int getMaxPatients() { return maxPatients; }
    public void setMaxPatients(int maxPatients) { this.maxPatients = maxPatients; }

    public BigDecimal getConsultationFee() { return consultationFee; }
    public void setConsultationFee(BigDecimal consultationFee) { this.consultationFee = consultationFee; }

    public int getCurrentQueueNo() { return currentQueueNo; }
    public void setCurrentQueueNo(int currentQueueNo) { this.currentQueueNo = currentQueueNo; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public int getBookedCount() { return bookedCount; }
    public void setBookedCount(int bookedCount) { this.bookedCount = bookedCount; }

    public int getWaitingCount() { return waitingCount; }
    public void setWaitingCount(int waitingCount) { this.waitingCount = waitingCount; }

    public int getAvailableSlots() { return Math.max(0, maxPatients - bookedCount); }

    public boolean isBookable() {
        return ("SCHEDULED".equals(status) || "ACTIVE".equals(status)) && getAvailableSlots() > 0;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("doctorId", doctorId);
        map.put("doctorName", doctorName);
        map.put("sessionDate", sessionDate);
        map.put("startTime", startTime);
        map.put("endTime", endTime);
        map.put("roomNo", roomNo);
        map.put("maxPatients", maxPatients);
        map.put("consultationFee", consultationFee);
        map.put("currentQueueNo", currentQueueNo);
        map.put("status", status);
        map.put("bookedCount", bookedCount);
        map.put("waitingCount", waitingCount);
        map.put("availableSlots", getAvailableSlots());
        map.put("bookable", isBookable());
        return map;
    }
}
