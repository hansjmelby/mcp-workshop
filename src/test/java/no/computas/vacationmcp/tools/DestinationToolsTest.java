package no.computas.vacationmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import no.computas.vacationmcp.domain.Destination;
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
}
