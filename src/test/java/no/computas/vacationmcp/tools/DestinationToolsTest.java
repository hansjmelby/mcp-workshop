package no.computas.vacationmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import no.computas.vacationmcp.domain.Destination;
import no.computas.vacationmcp.service.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * MCP-serveren er avskrudd i test (se {@code src/test/resources/application.properties}),
 * så verktøyet testes som den Spring-beanen det er. Selve protokoll-laget verifiseres
 * separat med stdio-røyktesten i {@code CLAUDE.md}.
 */
@SpringBootTest
class DestinationToolsTest {

    @Autowired
    private DestinationTools tools;

    @Test
    void returnsAllFiveSeededDestinations() {
        List<Destination> reisemaal = tools.listDestinations();

        // Repository-et sorterer på id, så rekkefølgen er den samme som i data.sql.
        assertEquals(
                List.of(
                        "Lofoten Rorbuer",
                        "Santorini Caldera",
                        "Kyoto Machiya",
                        "Toscana Agriturismo",
                        "Tromsø Nordlys-lodge"),
                reisemaal.stream().map(Destination::name).toList());
        assertTrue(reisemaal.stream().allMatch(Destination::available));
    }

    @Test
    void exposesNameCountryAndPricePerNight() {
        Destination lofoten = tools.listDestinations().get(0);

        assertEquals(1L, lofoten.id());
        assertEquals("Lofoten Rorbuer", lofoten.name());
        assertEquals("Norge", lofoten.country());
        assertEquals(1850.0, lofoten.pricePerNight());
    }

    @Test
    void searchesFreeTextInNameAndDescription() {
        // «nordlys» finnes bare i beskrivelsen til Tromsø-lodgen …
        assertEquals(
                List.of("Tromsø Nordlys-lodge"),
                navnene(tools.searchDestinations("nordlys", null, null)));

        // … mens «rorbu» treffer navnet (og beskrivelsen) til Lofoten.
        assertEquals(List.of("Lofoten Rorbuer"), navnene(tools.searchDestinations("rorbu", null, null)));
    }

    @Test
    void filtersByCountry() {
        assertEquals(
                List.of("Lofoten Rorbuer", "Tromsø Nordlys-lodge"),
                navnene(tools.searchDestinations(null, "Norge", null)));
    }

    @Test
    void filtersByMaxPricePerNight() {
        List<Destination> billige = tools.searchDestinations(null, null, 1850.0);

        // Grensen er inklusiv: Lofoten koster nøyaktig 1850.
        assertEquals(List.of("Lofoten Rorbuer", "Kyoto Machiya", "Toscana Agriturismo"), navnene(billige));
        assertTrue(billige.stream().allMatch(d -> d.pricePerNight() <= 1850.0));
    }

    @Test
    void combinesAllThreeFiltersWithAnd() {
        // Norge + pristak 2000 utelukker Tromsø (2100); fritekst «rorbu» beholder Lofoten.
        assertEquals(
                List.of("Lofoten Rorbuer"), navnene(tools.searchDestinations("rorbu", "Norge", 2000.0)));

        // Samme kombinasjon med et pristak under Lofoten-prisen gir tomt resultat.
        assertEquals(List.of(), navnene(tools.searchDestinations("rorbu", "Norge", 1000.0)));
    }

    @Test
    void withoutArgumentsSearchReturnsEverythingAvailable() {
        assertEquals(
                navnene(tools.listDestinations()), navnene(tools.searchDestinations(null, null, null)));
    }

    @Test
    void rejectsNegativeMaxPrice() {
        // Tjenesten validerer; verktøyet lar exception-en boble, og Spring AI gjør den om til
        // et tool-resultat med isError: true (se SOLUTION-STATUS.md, T-04).
        ValidationException feil =
                assertThrows(
                        ValidationException.class, () -> tools.searchDestinations(null, null, -1.0));

        assertEquals("maxPricePerNight kan ikke være negativ", feil.getMessage());
    }

    private static List<String> navnene(List<Destination> reisemaal) {
        return reisemaal.stream().map(Destination::name).toList();
    }
}
