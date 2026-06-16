package no.computas.vacationmcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import no.computas.vacationmcp.domain.Booking;
import no.computas.vacationmcp.domain.BookingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class BookingServiceTest {

    private static final long KYOTO = 3L; // 1600/natt, periode 2026-10-01..11-30, kapasitet 3

    @Autowired
    private BookingService bookings;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearBookings() {
        jdbc.update("DELETE FROM bookings");
    }

    @Test
    void createsBookingWithComputedPriceAndPendingStatus() {
        Booking booking = bookings.createBooking("Ola Nordmann", KYOTO,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8), 2);

        assertTrue(booking.id() > 0);
        assertEquals(BookingStatus.PENDING, booking.status());
        assertEquals(1600.0 * 3 * 2, booking.totalPrice(), 0.001);
        assertEquals(booking, bookings.get(booking.id()));
    }

    @Test
    void rejectsBookingThatExceedsCapacity() {
        bookings.createBooking("Kari", KYOTO, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8), 2);
        // 2 av 3 plasser tatt; en overlappende booking på 2 til ville blitt 4 > 3
        assertThrows(ValidationException.class, () -> bookings.createBooking(
                "Per", KYOTO, LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 9), 2));
    }

    @Test
    void allowsLegalStatusTransitionAndRejectsIllegal() {
        Booking booking = bookings.createBooking("Ada", KYOTO,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8), 1);

        Booking confirmed = bookings.updateStatus(booking.id(), BookingStatus.CONFIRMED);
        assertEquals(BookingStatus.CONFIRMED, confirmed.status());

        // PENDING/CONFIRMED -> COMPLETED er ulovlig (må innom PAID)
        assertThrows(ValidationException.class,
                () -> bookings.updateStatus(booking.id(), BookingStatus.COMPLETED));
    }

    @Test
    void cancellingFreesCapacity() {
        Booking full = bookings.createBooking("Gruppe", KYOTO,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8), 3); // fyller kapasiteten

        assertThrows(ValidationException.class, () -> bookings.createBooking(
                "Sent ute", KYOTO, LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 9), 1));

        bookings.cancel(full.id());

        Booking afterCancel = bookings.createBooking("Heldig", KYOTO,
                LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 9), 1);
        assertEquals(BookingStatus.PENDING, afterCancel.status());
    }

    @Test
    void getUnknownBookingThrows() {
        assertThrows(NotFoundException.class, () -> bookings.get(999L));
    }

    @Test
    void listFiltersByStatus() {
        Booking booking = bookings.createBooking("Liv", KYOTO,
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8), 1);
        bookings.updateStatus(booking.id(), BookingStatus.CONFIRMED);

        assertEquals(1, bookings.list(null).size());
        assertEquals(1, bookings.list(BookingStatus.CONFIRMED).size());
        assertTrue(bookings.list(BookingStatus.PENDING).isEmpty());
    }
}
