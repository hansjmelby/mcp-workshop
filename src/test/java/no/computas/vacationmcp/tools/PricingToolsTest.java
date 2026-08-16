package no.computas.vacationmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import no.computas.vacationmcp.service.NotFoundException;
import no.computas.vacationmcp.service.Quote;
import no.computas.vacationmcp.service.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * MCP-serveren er avskrudd i test (se {@code src/test/resources/application.properties}), så
 * verktøyet testes som den Spring-beanen det er. Protokoll-laget — {@code inputSchema} med
 * fylt {@code required}, og at feil kommer ut som {@code isError: true} — er verifisert med
 * stdio-røyktesten; se T-06 i {@code SOLUTION-STATUS.md}.
 *
 * <p>Tallene under er regnet ut for hånd mot seed-dataene i {@code data.sql}:
 * reisemål 1 (Lofoten Rorbuer) koster 1850 per natt og har to perioder — 2026-07-01→2026-08-31
 * <em>med</em> sesongpris 2200, og 2026-09-01→2026-10-31 <em>uten</em> sesongpris.
 */
@SpringBootTest
class PricingToolsTest {

    @Autowired
    private PricingTools tools;

    @Test
    void usesSeasonPriceWhenThePeriodHasOne() {
        // Sommerperioden: sesongpris 2200 × 9 netter × 2 reisende = 39 600.
        Quote tilbud = tools.getQuote(1, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10), 2);

        assertEquals(9, tilbud.nights());
        assertEquals(2200.0, tilbud.pricePerNight());
        assertEquals(39600.0, tilbud.totalPrice());
        // Sesongprisen er en annen enn reisemålets ordinære pris — det er nettopp den
        // forskjellen modellen skal kunne peke på i svaret til brukeren.
        assertEquals(1850.0, tilbud.destination().pricePerNight());
        assertNotEquals(tilbud.destination().pricePerNight(), tilbud.pricePerNight());
    }

    @Test
    void fallsBackToTheOrdinaryPricePerNightWhenTheSeasonPriceIsNull() {
        // Høstperioden (2026-09-01→2026-10-31) har season_price = NULL i data.sql,
        // så normalprisen gjelder: 1850 × 5 netter × 2 reisende = 18 500.
        Quote tilbud = tools.getQuote(1, LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 10), 2);

        assertEquals(5, tilbud.nights());
        assertEquals(1850.0, tilbud.pricePerNight());
        assertEquals(tilbud.destination().pricePerNight(), tilbud.pricePerNight());
        assertEquals(18500.0, tilbud.totalPrice());
    }

    @Test
    void echoesEveryTermOfTheCalculationSoTheModelCanExplainThePrice() {
        Quote tilbud = tools.getQuote(1, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10), 3);

        assertEquals(1L, tilbud.destination().id());
        assertEquals("Lofoten Rorbuer", tilbud.destination().name());
        assertEquals(LocalDate.of(2026, 7, 1), tilbud.from());
        assertEquals(LocalDate.of(2026, 7, 10), tilbud.to());
        assertEquals(3, tilbud.numTravelers());
        // Alle leddene henger sammen: pris/natt × netter × reisende == totalpris.
        assertEquals(
                tilbud.pricePerNight() * tilbud.nights() * tilbud.numTravelers(),
                tilbud.totalPrice());
    }

    @Test
    void oneTravelerIsTheLowerBoundAndIsAccepted() {
        // 2200 × 9 × 1 = 19 800.
        assertEquals(
                19800.0,
                tools.getQuote(1, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10), 1)
                        .totalPrice());
    }

    @Test
    void rejectsZeroTravelers() {
        ValidationException feil =
                assertThrows(
                        ValidationException.class,
                        () ->
                                tools.getQuote(
                                        1, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10), 0));

        assertEquals("antall reisende må være minst 1", feil.getMessage());
    }

    @Test
    void rejectsNegativeTravelers() {
        assertThrows(
                ValidationException.class,
                () -> tools.getQuote(1, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10), -2));
    }

    @Test
    void rejectsDatesOutsideAnyAvailabilityPeriod() {
        // Lofoten er bare åpent juli–oktober 2026; desember dekkes ikke av noen periode.
        ValidationException feil =
                assertThrows(
                        ValidationException.class,
                        () ->
                                tools.getQuote(
                                        1, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 10), 2));

        assertEquals("Ingen tilgjengelig periode dekker 2026-12-01 til 2026-12-10", feil.getMessage());
    }

    @Test
    void rejectsAStayThatSpansTwoAdjacentPeriods() {
        // 2026-08-15→2026-09-15 overlapper begge Lofoten-periodene (check_availability gir to
        // treff), men ingen enkelt periode dekker hele oppholdet — findCovering krever nettopp
        // det, så et tilbud kan ikke prises.
        assertThrows(
                ValidationException.class,
                () -> tools.getQuote(1, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 15), 2));
    }

    @Test
    void rejectsPartiallyCoveredStay() {
        // Starter innenfor sommerperioden, men slutter etter at den er over (31.08).
        assertThrows(
                ValidationException.class,
                () -> tools.getQuote(1, LocalDate.of(2026, 8, 25), LocalDate.of(2026, 9, 5), 2));
    }

    @Test
    void rejectsFromAfterTo() {
        assertEquals(
                "fra-dato må være før til-dato",
                assertThrows(
                                ValidationException.class,
                                () ->
                                        tools.getQuote(
                                                1,
                                                LocalDate.of(2026, 7, 10),
                                                LocalDate.of(2026, 7, 1),
                                                2))
                        .getMessage());
    }

    @Test
    void rejectsMissingDates() {
        assertEquals(
                "fra- og til-dato må oppgis",
                assertThrows(
                                ValidationException.class,
                                () -> tools.getQuote(1, null, LocalDate.of(2026, 7, 10), 2))
                        .getMessage());
    }

    /**
     * En ukjent id gir {@code NotFoundException}, ikke et tomt svar — i motsetning til
     * {@code check_availability}, som bare returnerer en tom liste. Forskjellen er skrevet inn
     * i beskrivelsen av begge verktøyene.
     */
    @Test
    void rejectsUnknownDestination() {
        NotFoundException feil =
                assertThrows(
                        NotFoundException.class,
                        () ->
                                tools.getQuote(
                                        999, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10), 2));

        assertTrue(feil.getMessage().contains("999"), feil.getMessage());
    }

    @Test
    void pricesTheOtherSeededDestinationsToo() {
        // Santorini: sesongpris 2900 (2026-06-01→2026-09-30) × 7 netter × 2 = 40 600.
        assertEquals(
                40600.0,
                tools.getQuote(2, LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 17), 2)
                        .totalPrice());
        // Toscana: ingen sesongpris, normalpris 1400 × 4 netter × 4 reisende = 22 400.
        assertEquals(
                22400.0,
                tools.getQuote(4, LocalDate.of(2026, 5, 10), LocalDate.of(2026, 5, 14), 4)
                        .totalPrice());
    }
}
