package no.computas.vacationmcp.domain;

import java.time.LocalDate;

/** En booking fra {@code bookings}-tabellen. */
public record Booking(
        long id,
        String customerName,
        long destinationId,
        LocalDate startDate,
        LocalDate endDate,
        int numTravelers,
        double totalPrice,
        BookingStatus status) {
}
