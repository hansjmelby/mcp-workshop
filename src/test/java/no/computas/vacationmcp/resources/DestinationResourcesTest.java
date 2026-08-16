package no.computas.vacationmcp.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import no.computas.vacationmcp.service.NotFoundException;
import no.computas.vacationmcp.service.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * MCP-serveren er avskrudd i test (se {@code src/test/resources/application.properties}), så
 * ressursene testes som de Spring-beanene de er. Selve protokoll-laget — at den ene havner i
 * {@code resources/list} og den andre i {@code resources/templates/list} — verifiseres med
 * stdio-røyktesten; tracen ligger i {@code SOLUTION-STATUS.md} (T-13).
 */
@SpringBootTest
class DestinationResourcesTest {

    @Autowired
    private DestinationResources resources;

    // --- Katalogen (statisk ressurs) ---------------------------------------------------

    @Test
    void catalogListsAllFiveSeededDestinations() {
        String markdown = resources.destinationCatalog();

        assertTrue(markdown.startsWith("# Reisemålskatalog\n"), markdown);
        assertTrue(markdown.contains("Katalogen inneholder 5 reisemål"), markdown);
        for (String navn :
                new String[] {
                    "Lofoten Rorbuer",
                    "Santorini Caldera",
                    "Kyoto Machiya",
                    "Toscana Agriturismo",
                    "Tromsø Nordlys-lodge"
                }) {
            assertTrue(markdown.contains(navn), "manglet " + navn + " i:\n" + markdown);
        }
    }

    @Test
    void catalogKeepsTheIdSoTheModelCanCallTheTools() {
        // Uten id-en i teksten kan ikke modellen gå videre til get_quote/create_booking.
        assertTrue(resources.destinationCatalog().contains("**Lofoten Rorbuer** (id 1) — Norge"));
    }

    @Test
    void catalogFormatsPriceWithoutTrailingDecimalZero() {
        String markdown = resources.destinationCatalog();

        assertTrue(markdown.contains("1850 kr per natt"), markdown);
        assertFalse(markdown.contains("1850.0"), markdown);
    }

    // --- Enkeltreisemål (resource template) --------------------------------------------

    @Test
    void singleDestinationRendersAllFields() {
        String markdown = resources.destination("3");

        assertTrue(markdown.startsWith("# Kyoto Machiya\n"), markdown);
        assertTrue(markdown.contains("- **id:** 3"), markdown);
        assertTrue(markdown.contains("- **Land:** Japan"), markdown);
        assertTrue(markdown.contains("- **Pris per natt:** 1600 kr"), markdown);
        assertTrue(markdown.contains("- **Åpent for booking:** ja"), markdown);
        assertTrue(markdown.contains("Historisk bytownhouse nær tempeldistriktet."), markdown);
    }

    @Test
    void unknownIdThrowsNotFoundWithAPointerToTheCatalog() {
        // Exception-en får boble, som i tools/-laget (T-04) — men utfallet er et annet:
        // resources/read har ingen isError-kanal, så klienten får en JSON-RPC-error der
        // meldingen vår er det eneste vi styrer. Da skal den være til hjelp.
        NotFoundException feil =
                assertThrows(NotFoundException.class, () -> resources.destination("999"));

        assertTrue(feil.getMessage().contains("id 999"), feil.getMessage());
        assertTrue(feil.getMessage().contains("destination://catalog"), feil.getMessage());
    }

    @Test
    void nonNumericIdIsRejectedByOurOwnParsing() {
        // URI-malen har ingen inputSchema som kan kreve et heltall — «abc» når helt fram til
        // metoden, og valideringen er vår.
        ValidationException feil =
                assertThrows(ValidationException.class, () -> resources.destination("abc"));

        assertTrue(feil.getMessage().contains("destination://{id}"), feil.getMessage());
    }

    // --- Annotasjonene: hva som gjør den ene statisk og den andre til en mal -------------

    @Test
    void catalogIsStaticAndSingleDestinationIsATemplate() throws Exception {
        McpResource katalog = annotasjon("destinationCatalog");
        McpResource enkelt = annotasjon("destination", String.class);

        // Spring AI splitter utelukkende på om URI-en inneholder en {variabel}:
        // uten → resources/list, med → resources/templates/list.
        assertEquals("destination://catalog", katalog.uri());
        assertFalse(katalog.uri().contains("{"));
        assertEquals("destination://{id}", enkelt.uri());
        assertTrue(enkelt.uri().contains("{id}"));

        // Innholdet er lesbar tekst, ikke JSON — se SOLUTION-STATUS.md (T-13).
        assertEquals("text/markdown", katalog.mimeType());
        assertEquals("text/markdown", enkelt.mimeType());
    }

    private static McpResource annotasjon(String metode, Class<?>... parametere) throws Exception {
        Method m = DestinationResources.class.getDeclaredMethod(metode, parametere);
        return m.getAnnotation(McpResource.class);
    }
}
