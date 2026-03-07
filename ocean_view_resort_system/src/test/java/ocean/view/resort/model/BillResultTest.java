package ocean.view.resort.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillResultTest {

    @Test
    @DisplayName("TC35: BillResult set and get subTotal and total")
    void setGetSubTotalTotal() {
        BillResult b = new BillResult();
        b.setSubTotal(new BigDecimal("10000"));
        b.setTotal(new BigDecimal("11000"));
        assertEquals(0, new BigDecimal("10000").compareTo(b.getSubTotal()));
        assertEquals(0, new BigDecimal("11000").compareTo(b.getTotal()));
    }

    @Test
    @DisplayName("TC36: BillResult tax and discount")
    void setGetTaxDiscount() {
        BillResult b = new BillResult();
        b.setTax(new BigDecimal("1000"));
        b.setDiscount(new BigDecimal("500"));
        assertEquals(0, new BigDecimal("1000").compareTo(b.getTax()));
        assertEquals(0, new BigDecimal("500").compareTo(b.getDiscount()));
    }

    @Test
    @DisplayName("TC37: BillResult nights and room details")
    void setGetNightsRoom() {
        BillResult b = new BillResult();
        b.setNights(3);
        b.setRoomNumber("201");
        b.setRoomType("DELUXE");
        assertEquals(3, b.getNights());
        assertEquals("201", b.getRoomNumber());
        assertEquals("DELUXE", b.getRoomType());
    }
}