package no.computas.vacationmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
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

    // --- T-08 · get_booking -------------------------------------------------------------

    /**
     * Alle åtte feltene sjekkes med vilje: {@code get_booking} er verktøyet en modell bruker for
     * å oppsummere en booking for brukeren, så et felt som faller ut på veien er en ekte feil,
     * ikke en detalj. Recorden går uendret fra tjenesten til tekstblokken (T-03-konvensjonen).
     */
    @Test
    void getsANewlyCreatedBookingWithAllFields() {
        Booking opprettet = tools.createBooking("Ola Nordmann", KYOTO, FROM, TO, 2);

        Booking hentet = tools.getBooking(opprettet.id());

        assertEquals(opprettet.id(), hentet.id());
        assertEquals("Ola Nordmann", hentet.customerName());
        assertEquals(KYOTO, hentet.destinationId());
        assertEquals(FROM, hentet.startDate());
        assertEquals(TO, hentet.endDate());
        assertEquals(2, hentet.numTravelers());
        assertEquals(9600.0, hentet.totalPrice()); // 1600 × 3 netter × 2 reisende
        assertEquals(BookingStatus.PENDING, hentet.status());
        // …og som helhet: oppslaget gir nøyaktig det create_booking returnerte.
        assertEquals(opprettet, hentet);
    }

    /** {@code idempotentHint = true} i praksis: samme id, samme svar. */
    @Test
    void repeatedLookupsReturnTheSameBooking() {
        long id = tools.createBooking("Kari", KYOTO, FROM, TO, 1).id();

        assertEquals(tools.getBooking(id), tools.getBooking(id));
    }

    /**
     * Ukjent id skal gi {@code NotFoundException} med en melding modellen kan bruke — den er
     * ordrett teksten klienten ser etter innpakningslinja fra Spring AI (se T-04).
     */
    @Test
    void rejectsUnknownBookingId() {
        NotFoundException feil =
                assertThrows(NotFoundException.class, () -> tools.getBooking(999L));

        assertEquals("Fant ingen booking med id 999", feil.getMessage());
    }

    /** En slettet/aldri-opprettet id er samme sak: ingen tom respons, men en feil. */
    @Test
    void rejectsLookupAfterTheBookingsAreGone() {
        long id = tools.createBooking("Ola", KYOTO, FROM, TO, 1).id();
        jdbc.update("DELETE FROM bookings");

        assertEquals(
                "Fant ingen booking med id " + id,
                assertThrows(NotFoundException.class, () -> tools.getBooking(id)).getMessage());
    }

    // --- T-09 · update_booking_status ---------------------------------------------------

    /**
     * Oppretter en booking i et eget datovindu innenfor Kyoto-perioden (2026-10-01→11-30).
     * Statustestene under trenger flere bookinger samtidig, og med ikke-overlappende vinduer
     * slipper de å konkurrere om kapasiteten på 3 — kapasitet er T-11 sitt tema, ikke T-09 sitt.
     */
    private Booking bookingStartingOn(int dayOfOctober) {
        return tools.createBooking(
                "Ola Nordmann",
                KYOTO,
                LocalDate.of(2026, 10, dayOfOctober),
                LocalDate.of(2026, 10, dayOfOctober + 3),
                1);
    }

    @Test
    void movesAPendingBookingToConfirmed() {
        Booking opprettet = bookingStartingOn(5);

        Booking oppdatert = tools.updateBookingStatus(opprettet.id(), BookingStatus.CONFIRMED);

        assertEquals(BookingStatus.CONFIRMED, oppdatert.status());
        // Endringen er lagret, ikke bare returnert.
        assertEquals(BookingStatus.CONFIRMED, tools.getBooking(opprettet.id()).status());
        // …og bare status er rørt: resten av raden er den samme.
        assertEquals(
                new Booking(
                        opprettet.id(),
                        opprettet.customerName(),
                        opprettet.destinationId(),
                        opprettet.startDate(),
                        opprettet.endDate(),
                        opprettet.numTravelers(),
                        opprettet.totalPrice(),
                        BookingStatus.CONFIRMED),
                oppdatert);
    }

    /** Hele den lovlige kjeden, ett steg om gangen — akseptkriteriet i T-09. */
    @Test
    void walksTheWholeLegalChainOneStepAtATime() {
        long id = bookingStartingOn(5).id();

        assertEquals(
                BookingStatus.CONFIRMED,
                tools.updateBookingStatus(id, BookingStatus.CONFIRMED).status());
        assertEquals(
                BookingStatus.PAID, tools.updateBookingStatus(id, BookingStatus.PAID).status());
        assertEquals(
                BookingStatus.COMPLETED,
                tools.updateBookingStatus(id, BookingStatus.COMPLETED).status());
        assertEquals(BookingStatus.COMPLETED, tools.getBooking(id).status());
    }

    /** Steg kan ikke hoppes over: {@code PENDING → PAID} er ikke en kant i tilstandsmaskinen. */
    @Test
    void rejectsSkippingAStepInTheChain() {
        long id = bookingStartingOn(5).id();

        assertEquals(
                "Ulovlig statusovergang: PENDING -> PAID",
                assertThrows(
                                ValidationException.class,
                                () -> tools.updateBookingStatus(id, BookingStatus.PAID))
                        .getMessage());
        assertEquals(BookingStatus.PENDING, tools.getBooking(id).status(), "ingenting skal endres");
    }

    /**
     * Eksempelet fra akseptkriteriet: {@code COMPLETED} er en endestasjon, så det går verken
     * bakover eller videre derfra. Meldingen er ordrett den klienten får (etter innpakningslinja
     * fra Spring AI, se T-04).
     */
    @Test
    void rejectsAnIllegalTransitionOutOfATerminalStatus() {
        long id = bookingStartingOn(5).id();
        tools.updateBookingStatus(id, BookingStatus.CONFIRMED);
        tools.updateBookingStatus(id, BookingStatus.PAID);
        tools.updateBookingStatus(id, BookingStatus.COMPLETED);

        assertEquals(
                "Ulovlig statusovergang: COMPLETED -> PENDING",
                assertThrows(
                                ValidationException.class,
                                () -> tools.updateBookingStatus(id, BookingStatus.PENDING))
                        .getMessage());
        assertEquals(
                "Ulovlig statusovergang: COMPLETED -> CANCELLED",
                assertThrows(
                                ValidationException.class,
                                () -> tools.updateBookingStatus(id, BookingStatus.CANCELLED))
                        .getMessage());
        assertEquals(BookingStatus.COMPLETED, tools.getBooking(id).status());
    }

    /**
     * {@code idempotentHint = true} i praksis. Hintet lover at gjentatte kall ikke gir
     * <em>ytterligere effekt</em> — ikke at kall nummer to svarer det samme. Her avvises det
     * andre kallet (en overgang til seg selv er ikke lov), men databasen er identisk etterpå,
     * og det er nettopp det hintet handler om.
     */
    @Test
    void repeatingTheSameUpdateChangesNothingFurther() {
        long id = bookingStartingOn(5).id();
        Booking etterFørsteKall = tools.updateBookingStatus(id, BookingStatus.CONFIRMED);

        assertEquals(
                "Ulovlig statusovergang: CONFIRMED -> CONFIRMED",
                assertThrows(
                                ValidationException.class,
                                () -> tools.updateBookingStatus(id, BookingStatus.CONFIRMED))
                        .getMessage());
        assertEquals(etterFørsteKall, tools.getBooking(id));
    }

    /** Kansellering er lovlig fra alle tre ikke-terminale statusene. */
    @Test
    void cancelsFromEveryNonTerminalStatus() {
        long fraPending = bookingStartingOn(5).id();

        long fraConfirmed = bookingStartingOn(10).id();
        tools.updateBookingStatus(fraConfirmed, BookingStatus.CONFIRMED);

        long fraPaid = bookingStartingOn(15).id();
        tools.updateBookingStatus(fraPaid, BookingStatus.CONFIRMED);
        tools.updateBookingStatus(fraPaid, BookingStatus.PAID);

        for (long id : new long[] {fraPending, fraConfirmed, fraPaid}) {
            assertEquals(
                    BookingStatus.CANCELLED,
                    tools.updateBookingStatus(id, BookingStatus.CANCELLED).status());
        }

        // CANCELLED er også en endestasjon — en kansellert booking kan ikke gjenopplives.
        assertEquals(
                "Ulovlig statusovergang: CANCELLED -> CONFIRMED",
                assertThrows(
                                ValidationException.class,
                                () -> tools.updateBookingStatus(fraPending, BookingStatus.CONFIRMED))
                        .getMessage());
    }

    /** Ukjent id: samme {@code NotFoundException} som i T-08, og ingenting endres. */
    @Test
    void rejectsStatusUpdateForAnUnknownBookingId() {
        assertEquals(
                "Fant ingen booking med id 999",
                assertThrows(
                                NotFoundException.class,
                                () -> tools.updateBookingStatus(999L, BookingStatus.CONFIRMED))
                        .getMessage());
    }

    // --- T-10 · list_bookings -----------------------------------------------------------

    /** Id-ene i en liste, i den rekkefølgen de kom — gjør forventningene lesbare. */
    private static List<Long> ider(List<Booking> bookinger) {
        return bookinger.stream().map(Booking::id).toList();
    }

    /**
     * {@code null} betyr «alle» — det er nettopp derfor parameteren er
     * {@code required = false} i skjemaet. Rekkefølgen er {@code ORDER BY id} fra
     * {@code BookingRepository}, altså eldste først.
     */
    @Test
    void listsAllBookingsWhenNoStatusIsGiven() {
        long første = bookingStartingOn(5).id();
        long andre = bookingStartingOn(10).id();
        long tredje = bookingStartingOn(15).id();

        assertEquals(List.of(første, andre, tredje), ider(tools.listBookings(null)));
    }

    /** Nyopprettede bookinger dukker opp uten noe ekstra steg — lista leses live. */
    @Test
    void newlyCreatedBookingsShowUpInTheList() {
        assertEquals(List.of(), tools.listBookings(null), "tom database før noe er opprettet");

        Booking opprettet = tools.createBooking("Ola Nordmann", KYOTO, FROM, TO, 2);

        List<Booking> etterpå = tools.listBookings(null);
        assertEquals(1, etterpå.size());
        // Elementet er byte for byte det create_booking returnerte — ingen mapping underveis.
        assertEquals(opprettet, etterpå.getFirst());

        Booking neste = bookingStartingOn(20);
        assertEquals(List.of(opprettet.id(), neste.id()), ider(tools.listBookings(null)));
    }

    /**
     * Filteret treffer én status om gangen. Her plasseres tre bookinger i hver sin status, og
     * hver av de tre spørringene skal gi nøyaktig sin egen.
     */
    @Test
    void filtersOnEachRelevantStatus() {
        long pending = bookingStartingOn(5).id();

        long confirmed = bookingStartingOn(10).id();
        tools.updateBookingStatus(confirmed, BookingStatus.CONFIRMED);

        long paid = bookingStartingOn(15).id();
        tools.updateBookingStatus(paid, BookingStatus.CONFIRMED);
        tools.updateBookingStatus(paid, BookingStatus.PAID);

        assertEquals(List.of(pending), ider(tools.listBookings(BookingStatus.PENDING)));
        assertEquals(List.of(confirmed), ider(tools.listBookings(BookingStatus.CONFIRMED)));
        assertEquals(List.of(paid), ider(tools.listBookings(BookingStatus.PAID)));
        // Uten filter kommer alle tre, fortsatt sortert på id.
        assertEquals(List.of(pending, confirmed, paid), ider(tools.listBookings(null)));
        // Ingen av dem har nådd endestasjonene ennå.
        assertEquals(List.of(), tools.listBookings(BookingStatus.COMPLETED));
        assertEquals(List.of(), tools.listBookings(BookingStatus.CANCELLED));
    }

    /** De to terminale statusene, som bare kan nås via {@code update_booking_status}. */
    @Test
    void filtersOnTheTerminalStatusesToo() {
        long fullført = bookingStartingOn(5).id();
        tools.updateBookingStatus(fullført, BookingStatus.CONFIRMED);
        tools.updateBookingStatus(fullført, BookingStatus.PAID);
        tools.updateBookingStatus(fullført, BookingStatus.COMPLETED);

        long avlyst = bookingStartingOn(10).id();
        tools.updateBookingStatus(avlyst, BookingStatus.CANCELLED);

        assertEquals(List.of(fullført), ider(tools.listBookings(BookingStatus.COMPLETED)));
        assertEquals(List.of(avlyst), ider(tools.listBookings(BookingStatus.CANCELLED)));
        // En kansellert booking blir liggende i lista — den forsvinner ikke.
        assertEquals(List.of(fullført, avlyst), ider(tools.listBookings(null)));
        assertEquals(List.of(), tools.listBookings(BookingStatus.PENDING));
    }

    /**
     * Et filter uten treff gir en <b>tom liste</b>, ikke en feil. Dette er beslutningen om
     * svarformen i praksis: verktøyet returnerer en bar liste (ingen konvolutt à la
     * {@code AvailabilityResult} i T-05), og at tomt er et gyldig svar sies i
     * {@code description} i stedet.
     */
    @Test
    void returnsAnEmptyListWhenTheFilterMatchesNothing() {
        bookingStartingOn(5); // én PENDING-booking finnes

        assertEquals(List.of(), tools.listBookings(BookingStatus.PAID));
        assertEquals(1, tools.listBookings(BookingStatus.PENDING).size());
    }

    /** Ingen bookinger i det hele tatt: også en tom liste, ikke en {@code NotFoundException}. */
    @Test
    void returnsAnEmptyListWhenThereAreNoBookingsAtAll() {
        assertEquals(List.of(), tools.listBookings(null));
        for (BookingStatus status : BookingStatus.values()) {
            assertEquals(List.of(), tools.listBookings(status), status.name());
        }
    }

    /** {@code idempotentHint = true} i praksis: samme spørring, samme svar. */
    @Test
    void repeatedListingsReturnTheSameResult() {
        bookingStartingOn(5);
        bookingStartingOn(10);

        assertEquals(tools.listBookings(null), tools.listBookings(null));
        assertEquals(
                tools.listBookings(BookingStatus.PENDING),
                tools.listBookings(BookingStatus.PENDING));
    }

    // --- T-11 · kapasitetsgrenser -------------------------------------------------------
    //
    // Regelen ligger i BookingService/BookingRepository og er ikke skrevet her; testene under
    // pinner ned *hvordan* den regner, siden det er det verktøyets description lover modellen.
    // Grensetilfellene som allerede er dekket over (én over grensen, hele kapasiteten i ett
    // kall, ikke-overlappende datoer) gjentas ikke.

    /** Kort skrivemåte for et opphold på Kyoto i oktober 2026. */
    private Booking book(String navn, int fraDag, int tilDag, int reisende) {
        return tools.createBooking(
                navn,
                KYOTO,
                LocalDate.of(2026, 10, fraDag),
                LocalDate.of(2026, 10, tilDag),
                reisende);
    }

    private String kapasitetsfeil(String navn, int fraDag, int tilDag, int reisende) {
        return assertThrows(
                        ValidationException.class, () -> book(navn, fraDag, tilDag, reisende))
                .getMessage();
    }

    /**
     * Grensen nås i flere steg, ikke i ett kall: 2 + 1 = 3 går gjennom, den fjerde plassen ikke.
     * Sammenligningen {@code numTravelers > remaining} er altså inklusiv — akkurat på grensen er
     * lovlig, én over er det ikke.
     */
    @Test
    void fillsTheCapacityInStepsAndRejectsOnlyTheRequestThatTipsItOver() {
        book("Først", 5, 8, 2);

        Booking siste = book("Akkurat på grensen", 6, 9, 1); // 2 + 1 = 3 = kapasiteten
        assertEquals(BookingStatus.PENDING, siste.status());

        assertEquals(
                "Ikke nok kapasitet i perioden: 0 ledige plasser, 1 forespurt",
                kapasitetsfeil("Én for sent", 7, 10, 1));
    }

    /**
     * Datointervallene er <b>halvåpne</b>: overlapp-predikatet er {@code start_date < to AND
     * end_date > from}, så et opphold som starter på utsjekksdagen til et annet kolliderer ikke.
     * Det er nettopp derfor {@code description} kan foreslå «flytt innsjekk én dag».
     */
    @Test
    void aStayStartingOnAnothersCheckoutDayDoesNotCountAgainstTheSameSeats() {
        book("Første uke", 5, 8, 3); // hele kapasiteten, 5.–8. oktober

        // Én dag inn i det andre oppholdet: overlapper, og da er det fullt.
        assertEquals(
                "Ikke nok kapasitet i perioden: 0 ledige plasser, 1 forespurt",
                kapasitetsfeil("Overlapper med én natt", 7, 11, 1));

        // Innsjekk nøyaktig på utsjekksdagen: ingen overlapp, full kapasitet igjen.
        assertEquals(BookingStatus.PENDING, book("Rett etterpå", 8, 11, 3).status());
    }

    /**
     * {@code status <> 'CANCELLED'} i {@code sumActiveTravelers} er hele mekanismen bak T-12:
     * plassene frigjøres i det statusen settes, uten at raden slettes. Her gjøres kanselleringen
     * med {@code update_booking_status} (T-09) — {@code cancel_booking} hører til T-12.
     */
    @Test
    void cancelledBookingsStopCountingAgainstTheCapacity() {
        long fullBooking = book("Fyller opp", 5, 8, 3).id();
        assertEquals(
                "Ikke nok kapasitet i perioden: 0 ledige plasser, 2 forespurt",
                kapasitetsfeil("Kommer for sent", 6, 9, 2));

        tools.updateBookingStatus(fullBooking, BookingStatus.CANCELLED);

        assertEquals(BookingStatus.PENDING, book("Kommer for sent", 6, 9, 2).status());
        // Raden er ikke borte — den teller bare ikke lenger med.
        assertEquals(BookingStatus.CANCELLED, tools.getBooking(fullBooking).status());
    }

    /**
     * Motstykket: alle de fire andre statusene teller likt. En {@code COMPLETED} booking holder
     * altså fortsatt på plassene sine i perioden — bare {@code CANCELLED} slipper dem.
     */
    @Test
    void everyStatusExceptCancelledKeepsHoldingItsSeats() {
        long id = book("Går gjennom livssyklusen", 5, 8, 3).id();

        for (BookingStatus status :
                List.of(BookingStatus.CONFIRMED, BookingStatus.PAID, BookingStatus.COMPLETED)) {
            tools.updateBookingStatus(id, status);
            assertEquals(
                    "Ikke nok kapasitet i perioden: 0 ledige plasser, 1 forespurt",
                    kapasitetsfeil("Prøver mens status er " + status, 6, 9, 1),
                    "status " + status + " skal fortsatt beslaglegge plassene");
        }
    }

    /**
     * Summen tas over <b>hele</b> det forespurte vinduet, ikke per dag. To bookinger med luft
     * mellom seg legges derfor sammen så snart ett opphold spenner over begge — selv om ingen
     * enkeltdag ville sprukket. Regelen er altså konservativ: den slipper aldri gjennom en
     * overbooket dag, men kan avvise et opphold som strengt tatt hadde fått plass.
     *
     * <p>Samtidig vises {@code Math.max(remaining, 0)} i meldingen: 2 + 2 mot en kapasitet på 3
     * gir {@code remaining = -1}, men modellen får «0 ledige plasser».
     */
    @Test
    void theSumCoversTheWholeRequestedWindowAndTheMessageNeverGoesNegative() {
        book("Tidlig i uka", 20, 22, 2);
        book("Sent i uka", 26, 28, 2);

        // 21.–27. overlapper begge. Ingen enkeltdag ville hatt mer enn 3 reisende (2 + 1),
        // men summen over vinduet er 4 — altså -1 ledige, klippet til 0 i meldingen.
        assertEquals(
                "Ikke nok kapasitet i perioden: 0 ledige plasser, 1 forespurt",
                kapasitetsfeil("Spenner over begge", 21, 27, 1));

        // Et kortere opphold i luften mellom dem går derimot fint.
        assertEquals(BookingStatus.PENDING, book("I luften mellom", 23, 25, 3).status());
    }

    /** Kapasiteten er per reisemål: et annet reisemål på nøyaktig samme datoer er upåvirket. */
    @Test
    void capacityIsCountedPerDestination() {
        book("Fyller Kyoto", 5, 8, 3);

        // Lofoten (id 1) er åpent 2026-09-01→10-31 med kapasitet 6 og uten sesongpris.
        Booking lofoten = tools.createBooking(
                "Fyller Lofoten",
                1L,
                LocalDate.of(2026, 10, 5),
                LocalDate.of(2026, 10, 8),
                6);
        assertEquals(BookingStatus.PENDING, lofoten.status());
        assertEquals(33300.0, lofoten.totalPrice()); // 1850 × 3 netter × 6 reisende

        // …og Kyoto er fortsatt fullt, uten at Lofoten-bookingen telte med noe sted.
        assertEquals(
                "Ikke nok kapasitet i perioden: 0 ledige plasser, 1 forespurt",
                kapasitetsfeil("Prøver Kyoto igjen", 5, 8, 1));
    }

    /**
     * Flere {@code availability}-rader summeres <b>ikke</b>. {@code findCovering} krever at
     * <em>én</em> rad dekker hele oppholdet, så et opphold over skjøten mellom to perioder
     * avvises før kapasiteten i det hele tatt regnes ut — selv når begge periodene er åpne og
     * har ledig plass. Feilen er derfor «Ingen tilgjengelig periode dekker …», ikke en
     * kapasitetsfeil.
     */
    @Test
    void aStayCrossingTheSeamBetweenTwoAvailabilityPeriodsIsRejectedBeforeCapacityIsCounted() {
        // Lofoten har to tilstøtende perioder: 07-01→08-31 (sesongpris 2200) og 09-01→10-31.
        assertEquals(
                "Ingen tilgjengelig periode dekker 2026-08-30 til 2026-09-02",
                assertThrows(
                                ValidationException.class,
                                () -> tools.createBooking(
                                        "Over skjøten",
                                        1L,
                                        LocalDate.of(2026, 8, 30),
                                        LocalDate.of(2026, 9, 2),
                                        2))
                        .getMessage());

        // Begge sidene av skjøten er åpne hver for seg — det er bare oppholdet på tvers som ikke går.
        assertEquals(
                6600.0, // sesongpris 2200 × 1 natt × 3 reisende
                tools.createBooking(
                                "Før skjøten",
                                1L,
                                LocalDate.of(2026, 8, 30),
                                LocalDate.of(2026, 8, 31),
                                3)
                        .totalPrice());
        assertEquals(
                5550.0, // ordinær pris 1850 × 1 natt × 3 reisende
                tools.createBooking(
                                "Etter skjøten",
                                1L,
                                LocalDate.of(2026, 9, 1),
                                LocalDate.of(2026, 9, 2),
                                3)
                        .totalPrice());
    }

    // --- T-12 · cancel_booking ----------------------------------------------------------
    //
    // BookingService.cancel(id) er én linje: updateStatus(id, CANCELLED). Testene under bruker
    // derfor med vilje det *nye* verktøyet som inngang, i stedet for å gjenta T-09/T-11 sine
    // kall via update_booking_status — poenget er at den dedikerte oppføringen i katalogen gir
    // nøyaktig samme resultat, inkludert den frigjorte kapasiteten.

    /** Kanselleringen lagres, og bare {@code status} er rørt — resten av raden er uendret. */
    @Test
    void cancelBookingSetsCancelledAndLeavesTheRestOfTheRowUntouched() {
        Booking opprettet = bookingStartingOn(5);

        Booking kansellert = tools.cancelBooking(opprettet.id());

        assertEquals(BookingStatus.CANCELLED, kansellert.status());
        // Endringen er lagret, ikke bare returnert.
        assertEquals(BookingStatus.CANCELLED, tools.getBooking(opprettet.id()).status());
        assertEquals(
                new Booking(
                        opprettet.id(),
                        opprettet.customerName(),
                        opprettet.destinationId(),
                        opprettet.startDate(),
                        opprettet.endDate(),
                        opprettet.numTravelers(),
                        opprettet.totalPrice(),
                        BookingStatus.CANCELLED),
                kansellert);
    }

    /**
     * Kansellering er lovlig fra alle tre ikke-terminale statusene — også gjennom det dedikerte
     * verktøyet, som ikke legger noen egen forhåndssjekk oppå tilstandsmaskinen.
     */
    @Test
    void cancelBookingWorksFromEveryNonTerminalStatus() {
        long fraPending = bookingStartingOn(5).id();

        long fraConfirmed = bookingStartingOn(10).id();
        tools.updateBookingStatus(fraConfirmed, BookingStatus.CONFIRMED);

        long fraPaid = bookingStartingOn(15).id();
        tools.updateBookingStatus(fraPaid, BookingStatus.CONFIRMED);
        tools.updateBookingStatus(fraPaid, BookingStatus.PAID);

        for (long id : new long[] {fraPending, fraConfirmed, fraPaid}) {
            assertEquals(BookingStatus.CANCELLED, tools.cancelBooking(id).status());
            assertEquals(BookingStatus.CANCELLED, tools.getBooking(id).status());
        }
    }

    /**
     * {@code idempotentHint = true} i praksis, og svaret på «hva skjer ved to kanselleringer på
     * rad?». Det andre kallet <em>feiler</em> — {@code CANCELLED} har et tomt sett med lovlige
     * overganger, så heller ikke til seg selv — men databasen er bit for bit den samme etterpå.
     * Hintet lover ingen ytterligere <em>effekt</em>, ikke at kall to svarer det samme.
     */
    @Test
    void cancellingAnAlreadyCancelledBookingIsRejectedButChangesNothing() {
        long id = bookingStartingOn(5).id();
        Booking etterFørsteKall = tools.cancelBooking(id);

        assertEquals(
                "Ulovlig statusovergang: CANCELLED -> CANCELLED",
                assertThrows(ValidationException.class, () -> tools.cancelBooking(id))
                        .getMessage());
        assertEquals(etterFørsteKall, tools.getBooking(id));
    }

    /** {@code COMPLETED} er den andre endestasjonen: et gjennomført opphold kan ikke avlyses. */
    @Test
    void rejectsCancellingACompletedBooking() {
        long id = bookingStartingOn(5).id();
        tools.updateBookingStatus(id, BookingStatus.CONFIRMED);
        tools.updateBookingStatus(id, BookingStatus.PAID);
        tools.updateBookingStatus(id, BookingStatus.COMPLETED);

        assertEquals(
                "Ulovlig statusovergang: COMPLETED -> CANCELLED",
                assertThrows(ValidationException.class, () -> tools.cancelBooking(id))
                        .getMessage());
        assertEquals(BookingStatus.COMPLETED, tools.getBooking(id).status());
    }

    /** Ukjent id: samme {@code NotFoundException} som i T-08/T-09, og ingenting endres. */
    @Test
    void rejectsCancelForAnUnknownBookingId() {
        assertEquals(
                "Fant ingen booking med id 999",
                assertThrows(NotFoundException.class, () -> tools.cancelBooking(999L))
                        .getMessage());
    }

    /**
     * <b>Akseptkriteriet i T-12:</b> frigjort kapasitet blir tilgjengelig igjen. Kyoto fylles helt
     * opp, den neste bookingen avvises, kanselleringen gjøres med {@code cancel_booking}, og
     * <em>nøyaktig samme</em> booking går deretter gjennom. T-11 viste det samme med
     * {@code update_booking_status}; her er det den dedikerte inngangen som frigjør plassene.
     */
    @Test
    void cancelBookingFreesTheCapacityForTheSameRequest() {
        long fullBooking = book("Fyller opp", 5, 8, 3).id();
        assertEquals(
                "Ikke nok kapasitet i perioden: 0 ledige plasser, 2 forespurt",
                kapasitetsfeil("Kommer for sent", 6, 9, 2));

        tools.cancelBooking(fullBooking);

        Booking slappGjennom = book("Kommer for sent", 6, 9, 2);
        assertEquals(BookingStatus.PENDING, slappGjennom.status());
        // Raden er ikke slettet — den ligger igjen som CANCELLED og teller bare ikke lenger med.
        assertEquals(BookingStatus.CANCELLED, tools.getBooking(fullBooking).status());
        assertEquals(
                List.of(fullBooking, slappGjennom.id()), ider(tools.listBookings(null)));
    }

    /**
     * Det åpenbare spørsmålet i T-12, pinnet ned: {@code cancel_booking(id)} og
     * {@code update_booking_status(id, CANCELLED)} er <b>funksjonelt identiske</b>.
     * {@code BookingService.cancel(id)} er bokstavelig talt {@code updateStatus(id, CANCELLED)},
     * så to like bookinger kansellert hver sin vei blir like på alt annet enn {@code id}. Det egne
     * verktøyet finnes for katalogens skyld (ett argument, et navn som treffer brukerens intensjon,
     * og en hint-blokk hosten kan gate på ved navn) — ikke fordi det gjør noe annet.
     */
    @Test
    void cancelBookingIsTheSameOperationAsUpdatingTheStatusToCancelled() {
        Booking viaDedikert = tools.cancelBooking(bookingStartingOn(5).id());
        Booking viaGenerisk =
                tools.updateBookingStatus(bookingStartingOn(10).id(), BookingStatus.CANCELLED);

        assertEquals(BookingStatus.CANCELLED, viaDedikert.status());
        assertEquals(viaDedikert.status(), viaGenerisk.status());
        // Alt annet enn id og datoene (som er valgt ulikt for å unngå kapasitetskonflikt) er likt.
        assertEquals(viaDedikert.customerName(), viaGenerisk.customerName());
        assertEquals(viaDedikert.destinationId(), viaGenerisk.destinationId());
        assertEquals(viaDedikert.numTravelers(), viaGenerisk.numTravelers());
        assertEquals(viaDedikert.totalPrice(), viaGenerisk.totalPrice());
    }
}
