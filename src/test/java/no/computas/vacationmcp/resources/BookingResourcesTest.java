package no.computas.vacationmcp.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.time.LocalDate;
import no.computas.vacationmcp.domain.Booking;
import no.computas.vacationmcp.domain.BookingStatus;
import no.computas.vacationmcp.service.BookingService;
import no.computas.vacationmcp.service.NotFoundException;
import no.computas.vacationmcp.service.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MCP-serveren er avskrudd i test (se {@code src/test/resources/application.properties}), så
 * ressursen testes som den Spring-beanen den er. Selve protokoll-laget — at {@code booking://{id}}
 * havner i {@code resources/templates/list} og at ukjent id blir en JSON-RPC-{@code error} —
 * verifiseres med stdio-røyktesten; tracen ligger i {@code SOLUTION-STATUS.md} (T-14).
 *
 * <p><b>Denne testklassen skriver til databasen</b> (i motsetning til
 * {@code DestinationResourcesTest}), og følger derfor opplegget fra {@code BookingToolsTest}:
 * {@code DELETE FROM bookings} både før og etter hver test, slik at kapasiteten er kjent uansett
 * hva som lå igjen fra en tidligere kjøring, og slik at bookinger herfra ikke lekker inn i andre
 * testklasser.
 *
 * <p>Tallene er regnet ut for hånd mot seed-dataene i {@code data.sql}: reisemål 3 (Kyoto Machiya,
 * Japan) koster 1600 per natt og har perioden 2026-10-01→2026-11-30 uten sesongpris, med
 * kapasitet 3.
 */
@SpringBootTest
class BookingResourcesTest {

    private static final long KYOTO = 3L;
    private static final LocalDate FROM = LocalDate.of(2026, 10, 5);
    private static final LocalDate TO = LocalDate.of(2026, 10, 8); // 3 netter

    @Autowired
    private BookingResources resources;

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
    void rendersEverythingNeededToSummariseTheBooking() {
        Booking booking = bookings.createBooking("Kari Nordmann", KYOTO, FROM, TO, 2);

        String markdown = resources.booking(String.valueOf(booking.id()));

        assertTrue(markdown.startsWith("# Booking %d — Kari Nordmann\n".formatted(booking.id())), markdown);
        assertTrue(markdown.contains("- **Kunde:** Kari Nordmann"), markdown);
        assertTrue(markdown.contains("- **Periode:** 2026-10-05 → 2026-10-08 (3 netter)"), markdown);
        assertTrue(markdown.contains("- **Antall reisende:** 2"), markdown);
        // 1600 × 3 netter × 2 reisende = 9600.
        assertTrue(markdown.contains("- **Totalpris:** 9600 kr"), markdown);
        assertTrue(markdown.contains("- **Status:** PENDING"), markdown);
    }

    @Test
    void resolvesTheDestinationNameSoTheResourceStandsOnItsOwn() {
        // Booking-recorden har bare destinationId. Det ekstra oppslaget er hele poenget: uten
        // navnet må modellen lese destination://3 i tillegg for å kunne si hva turen gjelder.
        Booking booking = bookings.createBooking("Ola", KYOTO, FROM, TO, 1);

        String markdown = resources.booking(String.valueOf(booking.id()));

        assertTrue(markdown.contains("- **Reisemål:** Kyoto Machiya (id 3), Japan"), markdown);
    }

    @Test
    void keepsTheIdSoTheModelCanCallTheTools() {
        Booking booking = bookings.createBooking("Ola", KYOTO, FROM, TO, 1);

        assertTrue(
                resources.booking(String.valueOf(booking.id()))
                        .contains("- **id:** %d — bruk denne i `get_booking`".formatted(booking.id())));
    }

    @Test
    void formatsPriceWithoutTrailingDecimalZero() {
        Booking booking = bookings.createBooking("Ola", KYOTO, FROM, TO, 1);

        String markdown = resources.booking(String.valueOf(booking.id()));

        assertTrue(markdown.contains("4800 kr"), markdown);
        assertFalse(markdown.contains("4800.0"), markdown);
    }

    @Test
    void singleNightIsWrittenInSingular() {
        Booking booking = bookings.createBooking(
                "Ola", KYOTO, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 6), 1);

        assertTrue(resources.booking(String.valueOf(booking.id())).contains("(1 natt)"));
    }

    @Test
    void listsTheLegalNextStatusesFromTheStateMachine() {
        Booking booking = bookings.createBooking("Ola", KYOTO, FROM, TO, 1);

        // PENDING -> CONFIRMED | CANCELLED, lest av BookingStatus.canTransitionTo — ikke skrevet
        // ned på nytt i ressursen.
        assertTrue(
                resources.booking(String.valueOf(booking.id()))
                        .contains("- **Lovlige neste statuser:** CONFIRMED, CANCELLED"));
    }

    @Test
    void terminalStatusSaysSoInsteadOfListingNothing() {
        Booking booking = bookings.createBooking("Ola", KYOTO, FROM, TO, 1);
        bookings.updateStatus(booking.id(), BookingStatus.CANCELLED);

        String markdown = resources.booking(String.valueOf(booking.id()));

        assertTrue(markdown.contains("- **Status:** CANCELLED — kansellert"), markdown);
        assertTrue(markdown.contains("- **Lovlige neste statuser:** ingen — dette er en endestatus"), markdown);
    }

    @Test
    void unknownIdThrowsNotFoundJustLikeInT13() {
        // Exception-en får boble; resources/read har ingen isError-kanal, så klienten får en
        // JSON-RPC-error der meldingen vår er det eneste vi styrer.
        NotFoundException feil =
                assertThrows(NotFoundException.class, () -> resources.booking("999999"));

        assertTrue(feil.getMessage().contains("999999"), feil.getMessage());
    }

    @Test
    void nonNumericIdIsRejectedByOurOwnParsing() {
        // URI-malen har ingen inputSchema — «abc» når helt fram til metoden.
        ValidationException feil =
                assertThrows(ValidationException.class, () -> resources.booking("abc"));

        assertTrue(feil.getMessage().contains("booking://{id}"), feil.getMessage());
        assertTrue(feil.getMessage().contains("list_bookings"), feil.getMessage());
    }

    @Test
    void theResourceIsATemplateAndServesMarkdown() throws Exception {
        McpResource annotasjon =
                BookingResources.class.getDeclaredMethod("booking", String.class)
                        .getAnnotation(McpResource.class);

        // {variabel} i URI-en ⇒ resources/templates/list, ikke resources/list.
        assertEquals("booking://{id}", annotasjon.uri());
        assertTrue(annotasjon.uri().contains("{id}"));
        assertEquals("booking", annotasjon.name());
        assertEquals("text/markdown", annotasjon.mimeType());
    }
}
