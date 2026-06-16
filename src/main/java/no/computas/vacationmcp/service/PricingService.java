package no.computas.vacationmcp.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import no.computas.vacationmcp.domain.Availability;
import no.computas.vacationmcp.domain.Destination;
import no.computas.vacationmcp.repository.AvailabilityRepository;
import no.computas.vacationmcp.repository.DestinationRepository;
import org.springframework.stereotype.Service;

/**
 * Pris- og tilgjengelighetslogikk. Validerer datoer og antall reisende, finner perioden
 * som dekker oppholdet, og beregner totalpris med sesongpris-fallback.
 */
@Service
public class PricingService {

    private final DestinationRepository destinations;
    private final AvailabilityRepository availability;

    public PricingService(DestinationRepository destinations, AvailabilityRepository availability) {
        this.destinations = destinations;
        this.availability = availability;
    }

    /**
     * Beregn et pristilbud.
     *
     * @throws ValidationException ved ugyldige datoer/antall, utilgjengelig reisemål, eller
     *                             ingen periode som dekker datoene
     * @throws NotFoundException   hvis reisemålet ikke finnes
     */
    public Quote quote(long destinationId, LocalDate from, LocalDate to, int numTravelers) {
        if (from == null || to == null) {
            throw new ValidationException("fra- og til-dato må oppgis");
        }
        if (!from.isBefore(to)) {
            throw new ValidationException("fra-dato må være før til-dato");
        }
        if (numTravelers < 1) {
            throw new ValidationException("antall reisende må være minst 1");
        }

        Destination destination = destinations.findById(destinationId)
                .orElseThrow(() -> new NotFoundException("Fant ingen destinasjon med id " + destinationId));
        if (!destination.available()) {
            throw new ValidationException("Reisemålet er ikke tilgjengelig: " + destination.name());
        }

        Availability period = findCoveringPeriod(destinationId, from, to);
        double pricePerNight = period.seasonPrice() != null ? period.seasonPrice() : destination.pricePerNight();
        long nights = ChronoUnit.DAYS.between(from, to);
        double totalPrice = pricePerNight * nights * numTravelers;

        return new Quote(destination, from, to, nights, numTravelers, pricePerNight, totalPrice);
    }

    /** Finn perioden som dekker [from, to], eller kast {@link ValidationException}. */
    public Availability findCoveringPeriod(long destinationId, LocalDate from, LocalDate to) {
        return availability.findCovering(destinationId, from, to)
                .orElseThrow(() -> new ValidationException(
                        "Ingen tilgjengelig periode dekker " + from + " til " + to));
    }
}
