package no.computas.vacationmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import no.computas.vacationmcp.domain.Availability;
import no.computas.vacationmcp.service.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * MCP-serveren er avskrudd i test (se {@code src/test/resources/application.properties}),
 * så verktøyet testes som den Spring-beanen det er. Selve protokoll-laget — inkludert at
 * {@code "2026-07-01"} blir en {@link LocalDate} og at et ugyldig format gir
 * {@code isError: true} — verifiseres med stdio-røyktesten; se T-05 i
 * {@code SOLUTION-STATUS.md}.
 *
 * <p>Periodene som testes mot er de seedede i {@code data.sql}: reisemål 1 (Lofoten) har
 * 2026-07-01→2026-08-31 med sesongpris 2200 og 2026-09-01→2026-10-31 uten sesongpris.
 */
@SpringBootTest
class AvailabilityToolsTest {

    @Autowired
    private AvailabilityTools tools;

    @Test
    void returnsOverlappingPeriodWithCapacityAndSeasonPrice() {
        AvailabilityTools.AvailabilityResult svar =
                tools.checkAvailability(1, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10));

        assertEquals(1, svar.matchingPeriods());
        assertEquals(1, svar.periods().size());

        Availability sommer = svar.periods().get(0);
        assertEquals(LocalDate.of(2026, 7, 1), sommer.startDate());
        assertEquals(LocalDate.of(2026, 8, 31), sommer.endDate());
        assertEquals(6, sommer.capacity());
        assertEquals(2200.0, sommer.seasonPrice());
    }

    @Test
    void echoesTheQueryBackSoAnEmptyListCanBeRead() {
        AvailabilityTools.AvailabilityResult svar =
                tools.checkAvailability(1, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10));

        assertEquals(1L, svar.destinationId());
        assertEquals(LocalDate.of(2026, 7, 1), svar.from());
        assertEquals(LocalDate.of(2026, 7, 10), svar.to());
    }

    @Test
    void spanningTwoPeriodsReturnsBothInStartDateOrder() {
        // 2026-08-15 → 2026-09-15 krysser overgangen mellom de to Lofoten-periodene.
        AvailabilityTools.AvailabilityResult svar =
                tools.checkAvailability(1, LocalDate.of(2026, 8, 15), LocalDate.of(2026, 9, 15));

        assertEquals(2, svar.matchingPeriods());
        assertEquals(
                java.util.List.of(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 1)),
                svar.periods().stream().map(Availability::startDate).toList());
        // Høstperioden har ingen sesongpris — da gjelder reisemålets ordinære pris per natt.
        assertNull(svar.periods().get(1).seasonPrice());
    }

    @Test
    void noOverlapGivesEmptyListAndNotAnError() {
        // Lofoten er bare åpent juli–oktober 2026; desember treffer ingenting.
        AvailabilityTools.AvailabilityResult svar =
                tools.checkAvailability(1, LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 10));

        assertEquals(0, svar.matchingPeriods());
        assertTrue(svar.periods().isEmpty());
        // Konvolutten er fortsatt fylt ut, så modellen ser hva den faktisk spurte om.
        assertEquals(LocalDate.of(2026, 12, 1), svar.from());
    }

    @Test
    void unknownDestinationAlsoGivesEmptyList() {
        AvailabilityTools.AvailabilityResult svar =
                tools.checkAvailability(999, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 10));

        assertEquals(0, svar.matchingPeriods());
    }

    @Test
    void rejectsFromAfterTo() {
        ValidationException feil =
                assertThrows(
                        ValidationException.class,
                        () ->
                                tools.checkAvailability(
                                        1, LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 1)));

        // Samme melding som PricingService bruker, så feilspråket er likt på tvers av verktøyene.
        assertEquals("fra-dato må være før til-dato", feil.getMessage());
    }

    @Test
    void rejectsEqualFromAndTo() {
        // Et opphold på null netter er ikke en periode.
        assertThrows(
                ValidationException.class,
                () -> tools.checkAvailability(1, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1)));
    }

    @Test
    void rejectsMissingDates() {
        assertEquals(
                "fra- og til-dato må oppgis",
                assertThrows(
                                ValidationException.class,
                                () -> tools.checkAvailability(1, null, LocalDate.of(2026, 7, 10)))
                        .getMessage());
    }

    /**
     * Formatvalideringen ligger ikke i verktøyet, men i deserialiseringen: en ugyldig streng
     * blir aldri en {@link LocalDate}. Testen dokumenterer det laget verktøyet lener seg på —
     * over protokollen svarer Spring AI «Conversion from JSON to java.time.LocalDate failed».
     */
    @Test
    void nonIsoDateStringsNeverBecomeALocalDate() {
        assertThrows(DateTimeParseException.class, () -> LocalDate.parse("01.07.2026"));
        assertThrows(DateTimeParseException.class, () -> LocalDate.parse("i morgen"));
        assertThrows(DateTimeParseException.class, () -> LocalDate.parse("2026-13-45"));
    }
}
