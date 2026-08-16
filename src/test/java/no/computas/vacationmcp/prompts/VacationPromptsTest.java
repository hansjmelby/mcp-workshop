package no.computas.vacationmcp.prompts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.modelcontextprotocol.spec.McpSchema.EmbeddedResource;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.LocalDate;
import no.computas.vacationmcp.domain.Booking;
import no.computas.vacationmcp.resources.BookingResources;
import no.computas.vacationmcp.service.BookingService;
import no.computas.vacationmcp.service.NotFoundException;
import no.computas.vacationmcp.service.ValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MCP-serveren er avskrudd i test (se {@code src/test/resources/application.properties}), så
 * promptene testes som de Spring-beanene de er. Selve protokoll-laget — hvordan {@code prompts/list}
 * og {@code prompts/get} ser ut, og hva et manglende obligatorisk argument faktisk gjør — er
 * verifisert med stdio-røyktesten; tracen ligger i {@code SOLUTION-STATUS.md} (T-15).
 *
 * <p>Testene for {@code travel_summary} skriver til databasen, og følger derfor opplegget fra
 * {@code BookingResourcesTest}: {@code DELETE FROM bookings} både før og etter hver test.
 */
@SpringBootTest
class VacationPromptsTest {

    private static final long KYOTO = 3L; // 1600 kr/natt, ledig 2026-10-01→2026-11-30, kapasitet 3

    @Autowired
    private VacationPrompts prompts;

    @Autowired
    private BookingResources bookingResources;

    @Autowired
    private BookingService bookings;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void clearBookings() {
        jdbc.update("DELETE FROM bookings");
    }

    // --- plan_vacation_within_budget: arbeidsflyten -------------------------------------

    @Test
    void budgetPromptSpellsOutTheToolChainInOrder() {
        GetPromptResult resultat =
                prompts.planVacationWithinBudget(45000.0, 2, null, "2026-07-01", "2026-07-08");

        String tekst = tekstenI(resultat, 0);
        assertTrue(tekst.contains("`check_availability`"), tekst);
        assertTrue(tekst.contains("`get_quote`"), tekst);
        assertTrue(tekst.contains("`create_booking`"), tekst);

        // Rekkefølgen er hele poenget med prompten — den finnes ikke i tools/list.
        assertTrue(
                tekst.indexOf("`check_availability`") < tekst.indexOf("`get_quote`"),
                "check_availability må komme før get_quote");
        assertTrue(
                tekst.indexOf("`get_quote`") < tekst.indexOf("`create_booking`"),
                "get_quote må komme før create_booking");
    }

    @Test
    void promptMessagesUseTheUserRoleNotAssistant() {
        // Returnerer man en bar String, pakker Spring AI den som en ASSISTANT-melding — altså
        // som om modellen allerede hadde sagt det. En promptmal er noe *brukeren* sier, så vi
        // returnerer GetPromptResult og setter rollen selv.
        GetPromptResult resultat = prompts.planVacationWithinBudget(45000.0, 2, null, null, null);

        assertEquals(1, resultat.messages().size());
        assertEquals(Role.USER, resultat.messages().get(0).role());
        assertNotNull(resultat.description(), "GetPromptResult skal ha en description");
        assertTrue(resultat.description().contains("45000"), resultat.description());
    }

    @Test
    void countryArgumentSwitchesFromListToSearch() {
        String medLand = tekstenI(prompts.planVacationWithinBudget(45000.0, 2, "Japan", null, null), 0);
        String utenLand = tekstenI(prompts.planVacationWithinBudget(45000.0, 2, null, null, null), 0);

        assertTrue(medLand.contains("`search_destinations`"), medLand);
        assertTrue(medLand.contains("«Japan»"), medLand);
        assertTrue(utenLand.contains("`list_destinations`"), utenLand);
    }

    @Test
    void periodIsConvertedToAMaxPricePerNight() {
        // 45000 / (7 netter × 2 reisende) = 3214,28… → 3214, som er akkurat det filteret
        // search_destinations tar. Uten periode finnes det ingen netter å dele på.
        String medPeriode =
                tekstenI(prompts.planVacationWithinBudget(45000.0, 2, null, "2026-07-01", "2026-07-08"), 0);

        assertTrue(medPeriode.contains("7 netter"), medPeriode);
        assertTrue(medPeriode.contains("`maxPricePerNight` = 3214"), medPeriode);

        String utenPeriode = tekstenI(prompts.planVacationWithinBudget(45000.0, 2, null, null, null), 0);
        assertFalse(utenPeriode.contains("maxPricePerNight"), utenPeriode);
        assertTrue(utenPeriode.contains("ikke bestemt datoer"), utenPeriode);
    }

    // --- plan_vacation_within_budget: valideringen ingen andre gjør for oss --------------

    @Test
    void missingRequiredArgumentsArriveAsNullAndAreRejectedHere() {
        // Det finnes ikke noe inputSchema for prompts: required=true er dokumentasjon, og et
        // manglende argument blir null i metoden. Meldingen må derfor være til hjelp.
        ValidationException utenBudsjett =
                assertThrows(
                        ValidationException.class,
                        () -> prompts.planVacationWithinBudget(null, 2, null, null, null));
        assertTrue(utenBudsjett.getMessage().contains("«budget»"), utenBudsjett.getMessage());

        ValidationException utenReisende =
                assertThrows(
                        ValidationException.class,
                        () -> prompts.planVacationWithinBudget(45000.0, null, null, null, null));
        assertTrue(utenReisende.getMessage().contains("«numTravelers»"), utenReisende.getMessage());
    }

    @Test
    void nonsensicalNumbersAreRejected() {
        assertThrows(
                ValidationException.class,
                () -> prompts.planVacationWithinBudget(0.0, 2, null, null, null));
        assertThrows(
                ValidationException.class,
                () -> prompts.planVacationWithinBudget(45000.0, 0, null, null, null));
    }

    @Test
    void periodIsOptionalAsAPairNotOneDateAtATime() {
        ValidationException feil =
                assertThrows(
                        ValidationException.class,
                        () -> prompts.planVacationWithinBudget(45000.0, 2, null, "2026-07-01", null));

        assertTrue(feil.getMessage().contains("«from» og «to» må oppgis sammen"), feil.getMessage());
    }

    @Test
    void datesAreParsedHereBecausePromptArgumentsHaveNoSchema() {
        // Verktøyene tar LocalDate direkte (T-05), men prompt-argumenter går ikke gjennom
        // Jackson: Spring AI konverterer bare String/Integer/Long/Double/Boolean. Derfor er
        // datoene String-er som vi parser selv — og da eier vi også feilmeldingen.
        ValidationException format =
                assertThrows(
                        ValidationException.class,
                        () ->
                                prompts.planVacationWithinBudget(
                                        45000.0, 2, null, "01.07.2026", "2026-07-08"));
        assertTrue(format.getMessage().contains("yyyy-MM-dd"), format.getMessage());

        ValidationException rekkefolge =
                assertThrows(
                        ValidationException.class,
                        () ->
                                prompts.planVacationWithinBudget(
                                        45000.0, 2, null, "2026-07-08", "2026-07-01"));
        assertTrue(rekkefolge.getMessage().contains("før til-dato"), rekkefolge.getMessage());
    }

    // --- travel_summary: prompten som peker på ressursen --------------------------------

    @Test
    void summaryEmbedsTheBookingResourceInsteadOfReformattingIt() {
        Booking booking =
                bookings.createBooking(
                        "Kari Nordmann", KYOTO, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 8), 2);

        GetPromptResult resultat = prompts.travelSummary(String.valueOf(booking.id()));

        assertEquals(2, resultat.messages().size(), "instruksjon + vedlegg");
        assertTrue(resultat.messages().stream().allMatch(m -> m.role() == Role.USER));

        // Melding 1 er instruksjonen.
        String instruksjon = tekstenI(resultat, 0);
        assertTrue(instruksjon.contains("reisesammendrag"), instruksjon);
        assertTrue(instruksjon.contains("`get_booking`"), instruksjon);

        // Melding 2 bærer selve ressursen — med URI og mimeType, ikke bare tekst.
        PromptMessage vedlegg = resultat.messages().get(1);
        EmbeddedResource ressurs = assertInstanceOf(EmbeddedResource.class, vedlegg.content());
        TextResourceContents innhold =
                assertInstanceOf(TextResourceContents.class, ressurs.resource());

        assertEquals("booking://" + booking.id(), innhold.uri());
        assertEquals("text/markdown", innhold.mimeType());

        // Selve poenget: innholdet er *det samme objektet* resources/read ville gitt. Teksten
        // er ikke skrevet på nytt her, den er gjenbrukt fra BookingResources.
        assertEquals(bookingResources.booking(String.valueOf(booking.id())), innhold.text());
        assertTrue(innhold.text().contains("Kyoto Machiya"), innhold.text());
    }

    @Test
    void summaryDescribesItselfWithTheResourceUri() {
        Booking booking =
                bookings.createBooking(
                        "Ola Nordmann", KYOTO, LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 7), 1);

        GetPromptResult resultat = prompts.travelSummary(String.valueOf(booking.id()));

        assertEquals("Reisesammendrag for booking://" + booking.id(), resultat.description());
    }

    @Test
    void summaryPropagatesTheSameErrorsAsTheResource() {
        // Feilene får boble, som i tools/- og resources/-laget. For prompts blir utfallet en
        // JSON-RPC-error (-32602), akkurat som for resources/read.
        NotFoundException ukjent =
                assertThrows(NotFoundException.class, () -> prompts.travelSummary("999999"));
        assertTrue(ukjent.getMessage().contains("999999"), ukjent.getMessage());

        ValidationException ikkeTall =
                assertThrows(ValidationException.class, () -> prompts.travelSummary("abc"));
        assertTrue(ikkeTall.getMessage().contains("list_bookings"), ikkeTall.getMessage());

        ValidationException mangler =
                assertThrows(ValidationException.class, () -> prompts.travelSummary(null));
        assertTrue(mangler.getMessage().contains("«bookingId»"), mangler.getMessage());
    }

    // --- Annotasjonene: hva som faktisk havner i prompts/list ----------------------------

    @Test
    void everyArgumentIsAnnotatedSoTheCatalogGetsRealDescriptions() throws Exception {
        // Uten @McpArg gir PromptAdapter argumentet beskrivelsen «Parameter of type String» og
        // required=false. Det er standardverdien, ikke et bevisst valg — så alle skal annoteres.
        for (Method metode : new Method[] {budsjettMetoden(), sammendragsMetoden()}) {
            for (Parameter parameter : metode.getParameters()) {
                McpArg arg = parameter.getAnnotation(McpArg.class);
                assertNotNull(arg, parameter + " mangler @McpArg");
                assertFalse(arg.name().isBlank(), parameter + " mangler navn");
                assertFalse(arg.description().isBlank(), parameter + " mangler beskrivelse");
            }
        }
    }

    @Test
    void requiredFlagsMatchWhatTheMethodActuallyDemands() throws Exception {
        // McpArg.required() har default false — motsatt av @McpToolParam, der alt er
        // obligatorisk med mindre du sier fra. De obligatoriske må derfor merkes eksplisitt.
        assertTrue(argument(budsjettMetoden(), "budget").required());
        assertTrue(argument(budsjettMetoden(), "numTravelers").required());
        assertFalse(argument(budsjettMetoden(), "country").required());
        assertFalse(argument(budsjettMetoden(), "from").required());
        assertFalse(argument(budsjettMetoden(), "to").required());
        assertTrue(argument(sammendragsMetoden(), "bookingId").required());
    }

    @Test
    void titleIsSetBecauseUnlikeMcpResourceItIsActuallyRead() throws Exception {
        // PromptAdapter.asPrompt sender title() videre inn i McpSchema.Prompt — i motsetning til
        // @McpResource, der SyncMcpResourceProvider ignorerer feltet (T-13). Uten en verdi ville
        // prompts/list fått "title":"" og hosten hatt bare snake_case-navnet å vise i menyen.
        assertEquals("Planlegg ferie innen budsjett", budsjettMetoden().getAnnotation(McpPrompt.class).title());
        assertEquals("Reisesammendrag", sammendragsMetoden().getAnnotation(McpPrompt.class).title());
        assertEquals(
                "plan_vacation_within_budget", budsjettMetoden().getAnnotation(McpPrompt.class).name());
        assertEquals("travel_summary", sammendragsMetoden().getAnnotation(McpPrompt.class).name());
    }

    // --- Hjelpere -----------------------------------------------------------------------

    private static String tekstenI(GetPromptResult resultat, int index) {
        return assertInstanceOf(TextContent.class, resultat.messages().get(index).content()).text();
    }

    private static McpArg argument(Method metode, String navn) {
        for (Parameter parameter : metode.getParameters()) {
            McpArg arg = parameter.getAnnotation(McpArg.class);
            if (arg != null && navn.equals(arg.name())) {
                return arg;
            }
        }
        throw new AssertionError("Fant ikke argumentet " + navn + " på " + metode.getName());
    }

    private static Method budsjettMetoden() throws Exception {
        return VacationPrompts.class.getDeclaredMethod(
                "planVacationWithinBudget",
                Double.class,
                Integer.class,
                String.class,
                String.class,
                String.class);
    }

    private static Method sammendragsMetoden() throws Exception {
        return VacationPrompts.class.getDeclaredMethod("travelSummary", String.class);
    }
}
