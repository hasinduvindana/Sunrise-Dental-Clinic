package model;

import java.util.LinkedHashMap;
import java.util.Map;

/** One medicine line inside a prescription. */
public class PrescriptionItem {

    private int id;
    private int prescriptionId;
    private String drugName;
    private String dosage;
    private String frequency;
    private Integer durationDays;
    private String instructions;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(int prescriptionId) { this.prescriptionId = prescriptionId; }

    public String getDrugName() { return drugName; }
    public void setDrugName(String drugName) { this.drugName = drugName; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }

    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("drugName", drugName);
        map.put("dosage", dosage);
        map.put("frequency", frequency);
        map.put("durationDays", durationDays);
        map.put("instructions", instructions);
        return map;
    }
}
