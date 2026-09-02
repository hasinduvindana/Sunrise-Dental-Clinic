package model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A patient bill. Built through Bill.Builder so the many optional money fields
 * do not turn into a nine-argument constructor.
 *
 * DESIGN PATTERN: Builder.
 */
public class Bill {

    private int id;
    private String billNo;
    private int appointmentId;
    private int patientId;
    private int doctorId;
    private BigDecimal consultationFee = BigDecimal.ZERO;
    private BigDecimal treatmentTotal = BigDecimal.ZERO;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal tax = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private String pricingStrategy = "STANDARD";
    private String status = "PENDING";
    private Integer generatedBy;
    private String createdAt;
    private String paidAt;

    // joined display fields
    private String patientName;
    private String patientNo;
    private String doctorName;
    private String appointmentNo;
    private BigDecimal amountPaid = BigDecimal.ZERO;

    private List<BillItem> items = new ArrayList<>();

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getBillNo() { return billNo; }
    public void setBillNo(String billNo) { this.billNo = billNo; }

    public int getAppointmentId() { return appointmentId; }
    public void setAppointmentId(int appointmentId) { this.appointmentId = appointmentId; }

    public int getPatientId() { return patientId; }
    public void setPatientId(int patientId) { this.patientId = patientId; }

    public int getDoctorId() { return doctorId; }
    public void setDoctorId(int doctorId) { this.doctorId = doctorId; }

    public BigDecimal getConsultationFee() { return consultationFee; }
    public void setConsultationFee(BigDecimal consultationFee) { this.consultationFee = consultationFee; }

    public BigDecimal getTreatmentTotal() { return treatmentTotal; }
    public void setTreatmentTotal(BigDecimal treatmentTotal) { this.treatmentTotal = treatmentTotal; }

    public BigDecimal getDiscount() { return discount; }
    public void setDiscount(BigDecimal discount) { this.discount = discount; }

    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public String getPricingStrategy() { return pricingStrategy; }
    public void setPricingStrategy(String pricingStrategy) { this.pricingStrategy = pricingStrategy; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(Integer generatedBy) { this.generatedBy = generatedBy; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getPaidAt() { return paidAt; }
    public void setPaidAt(String paidAt) { this.paidAt = paidAt; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getPatientNo() { return patientNo; }
    public void setPatientNo(String patientNo) { this.patientNo = patientNo; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getAppointmentNo() { return appointmentNo; }
    public void setAppointmentNo(String appointmentNo) { this.appointmentNo = appointmentNo; }

    public BigDecimal getAmountPaid() { return amountPaid; }
    public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }

    public List<BillItem> getItems() { return items; }
    public void setItems(List<BillItem> items) { this.items = items; }

    public BigDecimal getBalance() {
        return total.subtract(amountPaid == null ? BigDecimal.ZERO : amountPaid);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("billNo", billNo);
        map.put("appointmentId", appointmentId);
        map.put("appointmentNo", appointmentNo);
        map.put("patientId", patientId);
        map.put("patientNo", patientNo);
        map.put("patientName", patientName);
        map.put("doctorId", doctorId);
        map.put("doctorName", doctorName);
        map.put("consultationFee", consultationFee);
        map.put("treatmentTotal", treatmentTotal);
        map.put("discount", discount);
        map.put("tax", tax);
        map.put("total", total);
        map.put("amountPaid", amountPaid);
        map.put("balance", getBalance());
        map.put("pricingStrategy", pricingStrategy);
        map.put("status", status);
        map.put("createdAt", createdAt);
        map.put("paidAt", paidAt);
        List<Map<String, Object>> itemMaps = new ArrayList<>();
        for (BillItem item : items) {
            itemMaps.add(item.toMap());
        }
        map.put("items", itemMaps);
        return map;
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------
    public static class Builder {
        private final Bill bill = new Bill();

        public Builder appointment(int appointmentId, String appointmentNo) {
            bill.appointmentId = appointmentId;
            bill.appointmentNo = appointmentNo;
            return this;
        }

        public Builder patient(int patientId, String patientNo, String patientName) {
            bill.patientId = patientId;
            bill.patientNo = patientNo;
            bill.patientName = patientName;
            return this;
        }

        public Builder doctor(int doctorId, String doctorName) {
            bill.doctorId = doctorId;
            bill.doctorName = doctorName;
            return this;
        }

        public Builder consultationFee(BigDecimal fee) {
            bill.consultationFee = fee == null ? BigDecimal.ZERO : fee;
            return this;
        }

        public Builder item(BillItem item) {
            bill.items.add(item);
            return this;
        }

        public Builder treatmentTotal(BigDecimal amount) {
            bill.treatmentTotal = amount;
            return this;
        }

        public Builder discount(BigDecimal amount) {
            bill.discount = amount;
            return this;
        }

        public Builder tax(BigDecimal amount) {
            bill.tax = amount;
            return this;
        }

        public Builder total(BigDecimal amount) {
            bill.total = amount;
            return this;
        }

        public Builder pricingStrategy(String name) {
            bill.pricingStrategy = name;
            return this;
        }

        public Builder generatedBy(Integer userId) {
            bill.generatedBy = userId;
            return this;
        }

        public Bill build() {
            return bill;
        }
    }
}
