package model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** A settlement against a bill: at the counter, by card, or from the portal. */
public class Payment {

    private int id;
    private int billId;
    private BigDecimal amount;
    private String method = "CASH";
    private String reference;
    private Integer receivedBy;
    private String paidAt;

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getBillId() { return billId; }
    public void setBillId(int billId) { this.billId = billId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public Integer getReceivedBy() { return receivedBy; }
    public void setReceivedBy(Integer receivedBy) { this.receivedBy = receivedBy; }

    public String getPaidAt() { return paidAt; }
    public void setPaidAt(String paidAt) { this.paidAt = paidAt; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("billId", billId);
        map.put("amount", amount);
        map.put("method", method);
        map.put("reference", reference);
        map.put("paidAt", paidAt);
        return map;
    }
}
