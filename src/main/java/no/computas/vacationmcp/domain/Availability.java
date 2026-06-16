package no.computas.vacationmcp.domain;

import java.time.LocalDate;

/**
 * En periode et reisemål er tilgjengelig i, med kapasitet og valgfri sesongpris.
 *
 * @param seasonPrice spesialpris pr. natt i perioden; {@code null} betyr bruk
 *                    destinasjonens {@link Destination#pricePerNight()}.
 */
public record Availability(
        long id,
        long destinationId,
        LocalDate startDate,
        LocalDate endDate,
        int capacity,
        Double seasonPrice) {
}
