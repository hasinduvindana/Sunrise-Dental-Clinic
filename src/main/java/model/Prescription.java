package model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What the doctor writes at the end of a consultation. */
public class Prescription {

    private int id;
    private int appointmentId;
    private int doctorId;
    private int patientId;
    private String diagnosis;
    private String advice;
    private String createdAt;

    private String doctorName;
    private String patientName;
    private String appointmentNo;

    private List<PrescriptionItem> items = new ArrayList<>();

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getAppointmentId() { return appointmentId; }
    public void setAppointmentId(int appointmentId) { this.appointmentId = appointmentId; }

    public int getDoctorId() { return doctorId; }
    public void setDoctorId(int doctorId) { this.doctorId = doctorId; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public String getAdvice() { return advice; }
    public void setAdvice(String advice) { this.advice = advice; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getAppointmentNo() { return appointmentNo; }
    public void setAppointmentNo(String appointmentNo) { this.appointmentNo = appointmentNo; }

    public List<PrescriptionItem> getItems() { return items; }
    public void setItems(List<PrescriptionItem> items) { this.items = items; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("appointmentId", appointmentId);
        map.put("appointmentNo", appointmentNo);
        map.put("doctorId", doctorId);
        map.put("doctorName", doctorName);
        map.put("patientId", patientId);
        map.put("patientName", patientName);
        map.put("diagnosis", diagnosis);
        map.put("advice", advice);
        map.put("createdAt", createdAt);
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        for (PrescriptionItem item : items) {
            itemMaps.add(item.toMap());
        }
        map.put("items", itemMaps);
        return map;
    }
}
