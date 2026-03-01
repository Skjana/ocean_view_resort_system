package ocean.view.resort.model;

import java.time.Instant;

public class User {

    private String id;
    private String username;
    private String role;
    private String fullName;
    private String email;
    private boolean active;
    private Instant createdAt;
    private int accessLevel;
    private boolean canManageUsers;
    private boolean canConfigureRates;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public int getAccessLevel() { return accessLevel; }
    public void setAccessLevel(int accessLevel) { this.accessLevel = accessLevel; }
    public boolean isCanManageUsers() { return canManageUsers; }
    public void setCanManageUsers(boolean canManageUsers) { this.canManageUsers = canManageUsers; }
    public boolean isCanConfigureRates() { return canConfigureRates; }
    public void setCanConfigureRates(boolean canConfigureRates) { this.canConfigureRates = canConfigureRates; }
}
