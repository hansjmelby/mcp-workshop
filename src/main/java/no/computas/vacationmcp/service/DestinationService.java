package no.computas.vacationmcp.service;

import java.util.List;
import no.computas.vacationmcp.domain.Destination;
import no.computas.vacationmcp.repository.DestinationRepository;
import org.springframework.stereotype.Service;

/** Forretningslogikk for å liste og søke i reisemål. */
@Service
public class DestinationService {

    private final DestinationRepository destinations;

    public DestinationService(DestinationRepository destinations) {
        this.destinations = destinations;
    }

    /** Alle tilgjengelige reisemål. */
    public List<Destination> listAvailable() {
        return destinations.findAllAvailable();
    }

    /**
     * Søk blant tilgjengelige reisemål. Alle parametre er valgfrie ({@code null} = ikke filtrer).
     *
     * @throws ValidationException hvis {@code maxPricePerNight} er negativ
     */
    public List<Destination> search(String query, String country, Double maxPricePerNight) {
        if (maxPricePerNight != null && maxPricePerNight < 0) {
            throw new ValidationException("maxPricePerNight kan ikke være negativ");
        }
        return destinations.search(query, country, maxPricePerNight);
    }
}
