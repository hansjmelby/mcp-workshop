package no.computas.vacationmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import no.computas.vacationmcp.domain.Booking;
import no.computas.vacationmcp.domain.BookingStatus;
import no.computas.vacationmcp.service.BookingService;
import no.computas.vacationmcp.service.NotFoundException;
import no.computas.vacationmcp.service.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MCP-serveren er avskrudd i test (se {@code src/test/resources/application.properties}), så
 * verktøyet testes som den Spring-beanen det er. Protokoll-laget — {@code annotations} for et
 * skrivende verktøy, og at feil kommer ut som {@code isError: true} — er verifisert med
 * stdio-røyktesten; se T-07 i {@code SOLUTION-STATUS.md}.
 *
 * <p><b>Dette er den første testklassen som skriver til databasen.</b> Den følger opplegget fra
 * {@code BookingServiceTest}: {@code DELETE FROM bookings} før hver test, slik at kapasiteten er
 * kjent uansett hva som lå igjen fra en tidligere kjøring (test-databasen
 * {@code build/test-vacation.db} overlever mellom kjøringer). Samme opprydding kjøres også
 * <em>etter</em> hver test, så bookinger herfra ikke lekker inn i andre testklasser i samme
 * kjøring.
 *
 * <p>Tallene er regnet ut for hånd mot seed-dataene i {@code data.sql}: reisemål 3 (Kyoto
 * Machiya) koster 1600 per natt og har én periode, 2026-10-01→2026-11-30, uten sesongpris og
 * med <b>kapasitet 3</b> — den laveste i datasettet, og derfor den som gjør kapasitetsgrensen
 * enkel å treffe.
 */
@SpringBootTest
class BookingToolsTest {

    private static final long KYOTO = 3L;
    private static final LocalDate FROM = LocalDate.of(2026, 10, 5);
    private static final LocalDate TO = LocalDate.of(2026, 10, 8); // 3 netter

    @Autowired
    private BookingTools tools;

    @Autowired
    private BookingService bookings;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clearBookings() {
        jdbc.update("DELETE FROM bookings");
    }

    @Test
    void createsBookingWithIdPendingStatusAndComputedTotalPrice() {
        Booking booking = tools.createBooking("Ola Nordmann", KYOTO, FROM, TO, 2);

        assertTrue(booking.id() > 0, "bookingen skal ha fått en generert id");
        assertEquals(BookingStatus.PENDING, booking.status());
        // Kyoto har ingen sesongpris: 1600 × 3 netter × 2 reisende = 9600.
        assertEquals(9600.0, booking.totalPrice());
        // Feltene kommer tilbake slik de ble lagret — modellen skal kunne gjenta dem.
        assertEquals("Ola Nordmann", booking.customerName());
        assertEquals(KYOTO, booking.destinationId());
        assertEquals(FROM, booking.startDate());
        assertEquals(TO, booking.endDate());
        assertEquals(2, booking.numTravelers());
    }

    @Test
    void theReturnedBookingIsTheOneThatWasPersisted() {
        Booking booking = tools.createBooking("Kari", KYOTO, FROM, TO, 1);

        assertEquals(booking, bookings.get(booking.id()));
    }

    @Test
    void rejectsUnknownDestination() {
        NotFoundException feil = assertThrows(
                NotFoundException.class, () -> tools.createBooking("Ola", 999L, FROM, TO, 2));

        assertTrue(feil.getMessage().contains("999"), feil.getMessage());
    }

    @Test
    void rejectsDatesOutsideAnyAvailabilityPeriod() {
        // Kyoto er bare åpent oktober–november 2026.
        ValidationException feil = assertThrows(
                ValidationException.class,
                () -> tools.createBooking(
                        "Ola", KYOTO, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 5), 2));

        assertEquals(
                "Ingen tilgjengelig periode dekker 2026-12-01 til 2026-12-05", feil.getMessage());
    }

    @Test
    void rejectsZeroTravelers() {
        ValidationException feil = assertThrows(
                ValidationException.class, () -> tools.createBooking("Ola", KYOTO, FROM, TO, 0));

        assertEquals("antall reisende må være minst 1", feil.getMessage());
    }

    @Test
    void rejectsBlankCustomerName() {
        assertEquals(
                "kundenavn må oppgis",
                assertThrows(
                                ValidationException.class,
                                () -> tools.createBooking("", KYOTO, FROM, TO, 2))
                        .getMessage());
        assertEquals(
                "kundenavn må oppgis",
                assertThrows(
                                ValidationException.class,
                                () -> tools.createBooking("   ", KYOTO, FROM, TO, 2))
                        .getMessage());
        assertEquals(
                "kundenavn må oppgis",
                assertThrows(
                                ValidationException.class,
                                () -> tools.createBooking(null, KYOTO, FROM, TO, 2))
                        .getMessage());
    }

    @Test
    void rejectsFromAfterTo() {
        assertEquals(
                "fra-dato må være før til-dato",
                assertThrows(
                                ValidationException.class,
                                () -> tools.createBooking("Ola", KYOTO, TO, FROM, 2))
                        .getMessage());
    }

    /**
     * Akseptkriteriet for <b>T-11</b> (avvis overbooking), verifisert gjennom verktøyet fra
     * T-07: kapasiteten ligger i {@code BookingService}, og det som testes her er at meldingen
     * kommer uendret ut av verktøylaget — den er lesbar og sier både hvor mange plasser som er
     * igjen og hvor mange som ble bedt om, slik at modellen kan foreslå et lavere antall.
     */
    @Test
    void rejectsBookingBeyondCapacityWithAReadableMessage() {
        tools.createBooking("Kari", KYOTO, FROM, TO, 2); // 2 av 3 plasser tatt

        ValidationException feil = assertThrows(
                ValidationException.class,
                () -> tools.createBooking(
                        "Per", KYOTO, LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 9), 2));

        assertEquals(
                "Ikke nok kapasitet i perioden: 1 ledige plasser, 2 forespurt", feil.getMessage());
    }

    @Test
    void acceptsABookingThatFillsTheCapacityExactly() {
        Booking full = tools.createBooking("Gruppe", KYOTO, FROM, TO, 3);

        assertEquals(BookingStatus.PENDING, full.status());
        // Kapasiteten er brukt opp: neste overlappende booking avvises, med 0 ledige plasser.
        assertEquals(
                "Ikke nok kapasitet i perioden: 0 ledige plasser, 1 forespurt",
                assertThrows(
                                ValidationException.class,
                                () -> tools.createBooking(
                                        "Sent ute",
                                        KYOTO,
                                        LocalDate.of(2026, 10, 6),
                                        LocalDate.of(2026, 10, 9),
                                        1))
                        .getMessage());
    }

    @Test
    void capacityIsPerPeriodOverlapNotPerDestination() {
        tools.createBooking("Gruppe", KYOTO, FROM, TO, 3); // fyller 5.–8. oktober

        // Datoer som ikke overlapper er upåvirket — samme reisemål, samme periode.
        Booking senere = tools.createBooking(
                "Andre uke", KYOTO, LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 23), 3);

        assertEquals(BookingStatus.PENDING, senere.status());
        assertEquals(14400.0, senere.totalPrice()); // 1600 × 3 netter × 3 reisende
    }
}
