package ocean.view.resort.model;

import java.time.LocalDateTime;

public class MaintenanceIssue {
    private Integer id;
    private String roomId;
    private String title;
    private String description;
    private String category;  // AC, PLUMBING, ELECTRICAL, FURNITURE, OTHER
    private String status;    // OPEN, IN_PROGRESS, DONE
    private String assignedTo;
    private LocalDateTime reportedAt;
    private LocalDateTime resolvedAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public LocalDateTime getReportedAt() { return reportedAt; }
    public void setReportedAt(LocalDateTime reportedAt) { this.reportedAt = reportedAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
