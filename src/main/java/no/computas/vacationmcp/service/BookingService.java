package no.computas.vacationmcp.service;

import java.time.LocalDate;
import java.util.List;
import no.computas.vacationmcp.domain.Availability;
import no.computas.vacationmcp.domain.Booking;
import no.computas.vacationmcp.domain.BookingStatus;
import no.computas.vacationmcp.repository.BookingRepository;
import org.springframework.stereotype.Service;

/**
 * Forretningslogikk for bookinger: oppretting med full validering og kapasitetssjekk,
 * statusendringer styrt av tilstandsmaskinen, samt oppslag og kansellering.
 */
@Service
public class BookingService {

    private final BookingRepository bookings;
    private final PricingService pricing;

    public BookingService(BookingRepository bookings, PricingService pricing) {
        this.bookings = bookings;
        this.pricing = pricing;
    }

    /**
     * Opprett en booking. Validerer kunde, datoer, reisemål og antall (via {@link PricingService}),
     * beregner totalpris, og sjekker at kapasiteten i perioden ikke overskrides.
     *
     * @throws ValidationException ved ugyldig input eller manglende kapasitet
     * @throws NotFoundException   hvis reisemålet ikke finnes
     */
    public Booking createBooking(String customerName, long destinationId, LocalDate from, LocalDate to,
            int numTravelers) {
        if (customerName == null || customerName.isBlank()) {
            throw new ValidationException("kundenavn må oppgis");
        }

        Quote quote = pricing.quote(destinationId, from, to, numTravelers);
        Availability period = pricing.findCoveringPeriod(destinationId, from, to);

        int alreadyBooked = bookings.sumActiveTravelers(destinationId, from, to);
        int remaining = period.capacity() - alreadyBooked;
        if (numTravelers > remaining) {
            throw new ValidationException(
                    "Ikke nok kapasitet i perioden: %d ledige plasser, %d forespurt"
                            .formatted(Math.max(remaining, 0), numTravelers));
        }

        Booking toSave = new Booking(0, customerName.trim(), destinationId, from, to,
                numTravelers, quote.totalPrice(), BookingStatus.PENDING);
        long id = bookings.insert(toSave);
        return get(id);
    }

    /** @throws NotFoundException hvis bookingen ikke finnes */
    public Booking get(long id) {
        return bookings.findById(id)
                .orElseThrow(() -> new NotFoundException("Fant ingen booking med id " + id));
    }

    /** Alle bookinger, eventuelt filtrert på status ({@code null} = alle). */
    public List<Booking> list(BookingStatus status) {
        return status == null ? bookings.findAll() : bookings.findByStatus(status);
    }

    /**
     * Endre status etter tilstandsmaskinen.
     *
     * @throws ValidationException ved en ulovlig overgang
     * @throws NotFoundException   hvis bookingen ikke finnes
     */
    public Booking updateStatus(long id, BookingStatus target) {
        Booking booking = get(id);
        if (!booking.status().canTransitionTo(target)) {
            throw new ValidationException(
                    "Ulovlig statusovergang: %s -> %s".formatted(booking.status(), target));
        }
        bookings.updateStatus(id, target);
        return get(id);
    }

    /** Kanseller en booking (frigjør kapasitet). */
    public Booking cancel(long id) {
        return updateStatus(id, BookingStatus.CANCELLED);
    }
}
