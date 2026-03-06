package ocean.view.resort.model;

import java.math.BigDecimal;
import java.util.List;

public class Room {

    private String id;
    private String roomNumber;
    private String roomType;
    private int floor;
    private int maxOccupancy;
    private BigDecimal nightlyRate;
    private String viewDesc;
    private String amenitiesStr;
    private boolean available;
    /** Room status: AVAILABLE, CLEANING, OUT_OF_ORDER. Used with housekeeping. */
    private String roomStatus = "AVAILABLE";

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public String getRoomNumber() {
        return roomNumber;
    }
    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getRoomType() {
        return roomType;
    }
    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public int getFloor() {
        return floor;
    }
    public void setFloor(int floor) {
        this.floor = floor;
    }

    public int getMaxOccupancy() {
        return maxOccupancy;
    }
    public void setMaxOccupancy(int maxOccupancy) {
        this.maxOccupancy = maxOccupancy;
    }

    public BigDecimal getNightlyRate() {
        return nightlyRate;
    }
    public void setNightlyRate(BigDecimal nightlyRate) {
        this.nightlyRate = nightlyRate;
    }

    public String getViewDesc() {
        return viewDesc;
    }
    public void setViewDesc(String viewDesc) {
        this.viewDesc = viewDesc;
    }

    public String getAmenitiesStr() {
        return amenitiesStr;
    }
    public void setAmenitiesStr(String amenitiesStr) {
        this.amenitiesStr = amenitiesStr;
    }

    public boolean isAvailable() {
        return available;
    }
    public void setAvailable(boolean available) {
        this.available = available;
    }

    public String getRoomStatus() {
        return roomStatus != null ? roomStatus : "AVAILABLE";
    }
    public void setRoomStatus(String roomStatus) {
        this.roomStatus = roomStatus;
    }

    public List<String> getAmenities() {
        if (amenitiesStr == null || amenitiesStr.isBlank()) return List.of();
        return List.of(amenitiesStr.split(",\\s*"));
    }

}
