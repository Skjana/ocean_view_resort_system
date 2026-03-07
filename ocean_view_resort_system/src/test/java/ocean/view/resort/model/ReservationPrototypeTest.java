package ocean.view.resort.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ReservationPrototypeTest {

    private static Reservation sourceReservation() {
        Reservation r = new Reservation();
        r.setId("RES-0001");
        r.setGuestName("John Doe");
        r.setGuestAddress("123 Main St");
        r.setContactNumber("0771234567");
        r.setEmail("john@example.com");
        r.setNationality("Sri Lankan");
        r.setRoomId("R101");
        r.setCheckInDate(LocalDate.of(2025, 1, 10));
        r.setCheckOutDate(LocalDate.of(2025, 1, 13));
        r.setNights(3);
        r.setStatus("CHECKED_OUT");
        r.setSpecialRequests("Late checkout");
        r.setLoyalty(true);
        r.setCreatedBy("staff1");
        return r;
    }

    @Test
    @DisplayName("TC09: Clone has null id")
    void cloneHasNullId() {
        Reservation src = sourceReservation();
        Reservation clone = ReservationPrototype.cloneForRepeatBooking(src,
                LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 4));
        assertNull(clone.getId());
    }

    @Test
    @DisplayName("TC10: Clone copies guest name")
    void cloneCopiesGuestName() {
        Reservation src = sourceReservation();
        Reservation clone = ReservationPrototype.cloneForRepeatBooking(src,
                LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 4));
        assertEquals("John Doe", clone.getGuestName());
    }

    @Test
    @DisplayName("TC11: Clone sets new check-in and check-out dates")
    void cloneSetsNewDates() {
        LocalDate in = LocalDate.of(2025, 3, 1);
        LocalDate out = LocalDate.of(2025, 3, 5);
        Reservation clone = ReservationPrototype.cloneForRepeatBooking(sourceReservation(), in, out);
        assertEquals(in, clone.getCheckInDate());
        assertEquals(out, clone.getCheckOutDate());
    }

    @Test
    @DisplayName("TC12: Clone has roomId null")
    void cloneHasNullRoomId() {
        Reservation clone = ReservationPrototype.cloneForRepeatBooking(sourceReservation(),
                LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 4));
        assertNull(clone.getRoomId());
    }

    @Test
    @DisplayName("TC13: Clone status is CONFIRMED")
    void cloneStatusIsConfirmed() {
        Reservation clone = ReservationPrototype.cloneForRepeatBooking(sourceReservation(),
                LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 4));
        assertEquals("CONFIRMED", clone.getStatus());
    }

    @Test
    @DisplayName("TC14: Clone nights calculated from new dates")
    void cloneNightsCalculated() {
        Reservation clone = ReservationPrototype.cloneForRepeatBooking(sourceReservation(),
                LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 6));
        assertEquals(5, clone.getNights());
    }

    @Test
    @DisplayName("TC15: Clone copies loyalty flag")
    void cloneCopiesLoyalty() {
        Reservation clone = ReservationPrototype.cloneForRepeatBooking(sourceReservation(),
                LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 4));
        assertTrue(clone.isLoyalty());
    }

    @Test
    @DisplayName("TC16: Clone has createdBy null")
    void cloneCreatedByNull() {
        Reservation clone = ReservationPrototype.cloneForRepeatBooking(sourceReservation(),
                LocalDate.of(2025, 2, 1), LocalDate.of(2025, 2, 4));
        assertNull(clone.getCreatedBy());
    }

    @Test
    @DisplayName("TC17: Clone with null new dates has nights 0")
    void cloneNullDatesNightsZero() {
        Reservation clone = ReservationPrototype.cloneForRepeatBooking(sourceReservation(), null, null);
        assertEquals(0, clone.getNights());
    }
}