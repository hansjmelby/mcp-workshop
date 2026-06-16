package no.computas.vacationmcp.service;

import java.time.LocalDate;
import no.computas.vacationmcp.domain.Destination;

/**
 * Et pristilbud for et opphold.
 *
 * @param pricePerNight prisen som faktisk ble brukt (sesongpris hvis satt, ellers normalpris)
 * @param totalPrice    {@code pricePerNight × nights × numTravelers}
 */
public record Quote(
        Destination destination,
        LocalDate from,
        LocalDate to,
        long nights,
        int numTravelers,
        double pricePerNight,
        double totalPrice) {
}
