package ocean.view.resort.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReservationTest {

    @Test
    @DisplayName("TC29: Reservation set and get id")
    void setIdGetId() {
        Reservation r = new Reservation();
        r.setId("RES-0001");
        assertEquals("RES-0001", r.getId());
    }

    @Test
    @DisplayName("TC30: Reservation set and get guest name and contact")
    void setGetGuestDetails() {
        Reservation r = new Reservation();
        r.setGuestName("Jane Smith");
        r.setContactNumber("0779876543");
        assertEquals("Jane Smith", r.getGuestName());
        assertEquals("0779876543", r.getContactNumber());
    }

    @Test
    @DisplayName("TC31: Reservation set and get dates and nights")
    void setGetDatesNights() {
        Reservation r = new Reservation();
        LocalDate in = LocalDate.of(2025, 4, 1);
        LocalDate out = LocalDate.of(2025, 4, 4);
        r.setCheckInDate(in);
        r.setCheckOutDate(out);
        r.setNights(3);
        assertEquals(in, r.getCheckInDate());
        assertEquals(out, r.getCheckOutDate());
        assertEquals(3, r.getNights());
    }

    @Test
    @DisplayName("TC32: Reservation set and get status")
    void setGetStatus() {
        Reservation r = new Reservation();
        r.setStatus("CONFIRMED");
        assertEquals("CONFIRMED", r.getStatus());
    }

    @Test
    @DisplayName("TC33: Reservation set and get total amount")
    void setGetTotalAmount() {
        Reservation r = new Reservation();
        r.setTotalAmount(new BigDecimal("15000.00"));
        assertEquals(0, new BigDecimal("15000.00").compareTo(r.getTotalAmount()));
    }

    @Test
    @DisplayName("TC34: Reservation loyalty flag")
    void setGetLoyalty() {
        Reservation r = new Reservation();
        r.setLoyalty(true);
        assertTrue(r.isLoyalty());
    }

}
