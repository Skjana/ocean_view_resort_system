package ocean.view.resort.model;

import java.time.LocalDateTime;

public class HousekeepingLogEntry {
    private Integer id;
    private String roomId;
    private String status;
    private LocalDateTime notedAt;
    private String staffId;
    private String notes;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getNotedAt() { return notedAt; }
    public void setNotedAt(LocalDateTime notedAt) { this.notedAt = notedAt; }
    public String getStaffId() { return staffId; }
    public void setStaffId(String staffId) { this.staffId = staffId; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
