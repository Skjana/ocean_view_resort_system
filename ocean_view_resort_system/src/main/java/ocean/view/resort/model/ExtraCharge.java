package ocean.view.resort.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ExtraCharge {
    private Integer id;
    private String reservationId;
    private String description;
    private BigDecimal amount;
    private LocalDateTime chargedAt;
    private String createdBy;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDateTime getChargedAt() { return chargedAt; }
    public void setChargedAt(LocalDateTime chargedAt) { this.chargedAt = chargedAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
