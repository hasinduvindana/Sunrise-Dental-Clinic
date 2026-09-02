package model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A scan, X-ray or lab sheet belonging to a patient. The brief allows text
 * files for storage, so the file itself is written to a folder on the server
 * disk and only its metadata is kept in MySQL.
 */
public class MedicalReport {

    private int id;
    private int patientId;
    private Integer appointmentId;
    private String title;
    private String fileName;
    private String filePath;
    private String contentType;
    private Integer uploadedBy;
    private String createdAt;

    private String patientName;
    private String uploadedByName;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public Integer getAppointmentId() { return appointmentId; }
    public void setAppointmentId(Integer appointmentId) { this.appointmentId = appointmentId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Integer getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(Integer uploadedBy) { this.uploadedBy = uploadedBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getUploadedByName() { return uploadedByName; }
    public void setUploadedByName(String uploadedByName) { this.uploadedByName = uploadedByName; }

    /** File path stays on the server: the client only ever gets a download URL. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("patientId", patientId);
        map.put("patientName", patientName);
        map.put("appointmentId", appointmentId);
        map.put("title", title);
        map.put("fileName", fileName);
        map.put("contentType", contentType);
        map.put("uploadedByName", uploadedByName);
        map.put("createdAt", createdAt);
        map.put("downloadUrl", "api/medical-reports/" + id + "/file");
        return map;
    }
}
