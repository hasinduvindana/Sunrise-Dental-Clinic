package model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** One line on a bill: a treatment, a consultation fee or an ad-hoc charge. */
public class BillItem {

    private int id;
    private int billId;
    private Integer treatmentId;
    private String description;
    private int quantity = 1;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal lineTotal = BigDecimal.ZERO;

    public BillItem() { }

    public BillItem(Integer treatmentId, String description, int quantity, BigDecimal unitPrice) {
        this.treatmentId = treatmentId;
        this.description = description;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getBillId() { return billId; }
    public void setBillId(int billId) { this.billId = billId; }

    public Integer getTreatmentId() { return treatmentId; }
    public void setTreatmentId(Integer treatmentId) { this.treatmentId = treatmentId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("treatmentId", treatmentId);
        map.put("description", description);
        map.put("quantity", quantity);
        map.put("unitPrice", unitPrice);
        map.put("lineTotal", lineTotal);
        return map;
    }
}
