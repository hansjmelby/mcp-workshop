package no.computas.vacationmcp.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import no.computas.vacationmcp.domain.Destination;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DestinationServiceTest {

    @Autowired
    private DestinationService service;

    @Test
    void listsAllSeededDestinations() {
        assertEquals(5, service.listAvailable().size());
    }

    @Test
    void filtersByCountry() {
        List<Destination> norske = service.search(null, "Norge", null);
        assertEquals(2, norske.size());
        assertTrue(norske.stream().allMatch(d -> d.country().equals("Norge")));
    }

    @Test
    void filtersByMaxPrice() {
        List<Destination> rimelige = service.search(null, null, 1600.0);
        assertEquals(2, rimelige.size()); // Toscana (1400) og Kyoto (1600)
        assertTrue(rimelige.stream().allMatch(d -> d.pricePerNight() <= 1600.0));
    }

    @Test
    void filtersByQueryCaseInsensitive() {
        List<Destination> treff = service.search("lofoten", null, null);
        assertEquals(1, treff.size());
        assertEquals("Lofoten Rorbuer", treff.get(0).name());
    }

    @Test
    void rejectsNegativeMaxPrice() {
        assertThrows(ValidationException.class, () -> service.search(null, null, -1.0));
    }
}
