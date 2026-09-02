package model;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** An item from the clinic's price list. */
public class Treatment {

    private int id;
    private String code;
    private String name;
    private BigDecimal basePrice;
    private int durationMinutes = 30;
    private String status = "ACTIVE";

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }

    public int getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(int durationMinutes) { this.durationMinutes = durationMinutes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("code", code);
        map.put("name", name);
        map.put("basePrice", basePrice);
        map.put("durationMinutes", durationMinutes);
        map.put("status", status);
        return map;
    }
}
