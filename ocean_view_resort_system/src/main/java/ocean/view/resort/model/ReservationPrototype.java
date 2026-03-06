package ocean.view.resort.model;


import java.time.LocalDate;

public final class ReservationPrototype {

    private ReservationPrototype() {}

    public static Reservation cloneForRepeatBooking(Reservation source, LocalDate newCheckIn, LocalDate newCheckOut) {
        Reservation r = new Reservation();
        r.setId(null);
        r.setGuestName(source.getGuestName());
        r.setGuestAddress(source.getGuestAddress());
        r.setContactNumber(source.getContactNumber());
        r.setEmail(source.getEmail());
        r.setNationality(source.getNationality());
        r.setRoomId(null);
        r.setCheckInDate(newCheckIn);
        r.setCheckOutDate(newCheckOut);
        r.setNights(newCheckIn != null && newCheckOut != null ? (int) java.time.temporal.ChronoUnit.DAYS.between(newCheckIn, newCheckOut) : 0);
        r.setStatus("CONFIRMED");
        r.setSpecialRequests(source.getSpecialRequests());
        r.setSubTotal(null);
        r.setTaxAmount(null);
        r.setDiscount(null);
        r.setTotalAmount(null);
        r.setLoyalty(source.isLoyalty());
        r.setCreatedBy(null);
        r.setCreatedAt(null);
        return r;
    }

}
