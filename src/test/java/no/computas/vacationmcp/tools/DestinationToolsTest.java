package no.computas.vacationmcp.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.spec.McpSchema.Content;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageRequest.ContextInclusionStrategy;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult;
import io.modelcontextprotocol.spec.McpSchema.CreateMessageResult.StopReason;
import io.modelcontextprotocol.spec.McpSchema.ImageContent;
import io.modelcontextprotocol.spec.McpSchema.ModelHint;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.function.Consumer;
import no.computas.vacationmcp.domain.Destination;
import no.computas.vacationmcp.service.ValidationException;
import no.computas.vacationmcp.tools.DestinationTools.Recommendation;
import no.computas.vacationmcp.tools.DestinationTools.RecommendationOutcome;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.mcp.annotation.context.DefaultSamplingSpec;
import org.springframework.ai.mcp.annotation.context.McpRequestContextTypes.SamplingSpec;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
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

    // =================================================================================
    // T-21 · recommend_destination (sampling)
    //
    // Som i T-20 stopper testene ved grensen: McpSyncRequestContext er mocket, så
    // JSON-RPC-runden mot hosten er ikke med. Det som testes er alt på VÅR side —
    // capability-sjekken, fallbacken, forespørselen vi bygger (systemPrompt, katalogen i
    // meldingen, modelPreferences, maxTokens, includeContext) og hvordan svaret tolkes.
    // At sampling/createMessage faktisk går på tråden er verifisert med en egen
    // Node-klient utenfor testene; se T-21 i SOLUTION-STATUS.md.
    // =================================================================================

    /**
     * Fanger forespørselen verktøyet bygger. Arver {@link DefaultSamplingSpec} i stedet for å
     * implementere {@link SamplingSpec} for hånd (16 metoder): feltene der er {@code protected},
     * og ved å arve tester vi mot rammeverkets <em>egen</em> byggelogikk — inkludert
     * default-metoden {@code message(String…)}, som pakker teksten i {@code TextContent} med
     * rollen {@code user}.
     */
    private static final class ForespoerselsFanger extends DefaultSamplingSpec {

        String melding() {
            return messages.stream()
                    .map(m -> m.content() instanceof TextContent t ? t.text() : "")
                    .reduce("", String::concat);
        }

        io.modelcontextprotocol.spec.McpSchema.ModelPreferences preferanser() {
            return modelPreferences;
        }

        String systemPrompt() {
            return systemPrompt;
        }

        Integer maxTokens() {
            return maxTokens;
        }

        Double temperature() {
            return temperature;
        }

        ContextInclusionStrategy kontekst() {
            return includeContextStrategy;
        }
    }

    private final ForespoerselsFanger fanger = new ForespoerselsFanger();

    /** En klient som låner ut modellen sin og svarer med det innholdet testen bestemmer. */
    private McpSyncRequestContext klientMedModell(Content svar, StopReason stopReason) {
        McpSyncRequestContext ctx = mock(McpSyncRequestContext.class);
        when(ctx.sampleEnabled()).thenReturn(true);
        when(ctx.sample(ArgumentMatchers.<Consumer<SamplingSpec>>any()))
                .thenAnswer(
                        kall -> {
                            // Kjør verktøyets egen spec-lambda, slik Spring AI ville gjort.
                            kall.<Consumer<SamplingSpec>>getArgument(0).accept(fanger);
                            return new CreateMessageResult(
                                    Role.ASSISTANT, svar, "en-modell-hosten-valgte", stopReason, null);
                        });
        return ctx;
    }

    private McpSyncRequestContext klientMedModell(String tekst) {
        return klientMedModell(new TextContent(null, tekst, null), StopReason.END_TURN);
    }

    /** En klient uten sampling i capabilities — altså de aller fleste, Claude Code inkludert. */
    private McpSyncRequestContext klientUtenSampling() {
        McpSyncRequestContext ctx = mock(McpSyncRequestContext.class);
        when(ctx.sampleEnabled()).thenReturn(false);
        return ctx;
    }

    /**
     * <b>Fallback-veien, og den viktigste testen her.</b> Sampling er den minst utbredte
     * MCP-primitiven, så dette er hovedveien i praksis. Uten capability skal verktøyet ikke
     * kaste og ikke henge — det skal levere katalogen med beskjed om at modellen som kalte
     * verktøyet får skrive anbefalingen selv. Merk at {@code sample(...)} aldri kalles: hadde vi
     * latt Spring AI oppdage det, hadde modellen fått en {@code IllegalStateException} i fanget.
     */
    @Test
    void fallsBackToTheCatalogWhenTheClientCannotSample() {
        McpSyncRequestContext ctx = klientUtenSampling();

        Recommendation svar = tools.recommendDestination(ctx, null);

        assertEquals(RecommendationOutcome.SAMPLING_NOT_SUPPORTED, svar.outcome());
        assertNull(svar.recommendation(), "det finnes ingen modellgenerert tekst");
        assertNull(svar.model());
        // Hele grunnlaget er med — det er selve leveransen på denne veien.
        assertEquals(5, svar.catalog().size());
        assertTrue(
                svar.message().contains("catalog"),
                "meldingen skal peke modellen på grunnlaget den faktisk har fått");
        verify(ctx, never()).sample(ArgumentMatchers.<Consumer<SamplingSpec>>any());
    }

    /** Det glade tilfellet: hosten låner ut modellen, og vi sender teksten videre. */
    @Test
    void returnsTheTextTheHostsModelWrote() {
        Recommendation svar =
                tools.recommendDestination(
                        klientMedModell("  Dagens tips er Kyoto Machiya (id 3).  "), null);

        assertEquals(RecommendationOutcome.RECOMMENDED, svar.outcome());
        assertEquals("Dagens tips er Kyoto Machiya (id 3).", svar.recommendation());
        // Hosten sier selv hvilken modell som svarte — vi ba bare om hint.
        assertEquals("en-modell-hosten-valgte", svar.model());
        assertFalse(svar.truncated());
        assertEquals(5, svar.catalog().size(), "grunnlaget følger med, så svaret kan kontrolleres");
    }

    /**
     * Forespørselen serveren sender. {@code systemPrompt} bærer regelen som holder modellen
     * innenfor katalogen, og meldingen bærer selve katalogen — med id-ene, som er broa videre til
     * pris- og bookingverktøyene.
     */
    @Test
    void buildsTheSamplingRequestFromTheCatalog() {
        tools.recommendDestination(klientMedModell("…"), null);

        assertNotNull(fanger.systemPrompt());
        assertTrue(
                fanger.systemPrompt().contains("utelukkende reisemål fra lista"),
                "systemPrompt skal forby modellen å finne på reisemål");

        String melding = fanger.melding();
        for (String navn : navnene(tools.listDestinations())) {
            assertTrue(melding.contains(navn), "katalogen skal stå i meldingen: " + navn);
        }
        assertTrue(melding.contains("id 3: Kyoto Machiya (Japan) — 1600 kr per natt"));
        assertTrue(melding.contains("Velg ETT"), "oppgaven skal stå i meldingen");
    }

    /**
     * {@code modelPreferences} er hint, ikke krav — og hintene er <em>familienavn</em>, ikke
     * modell-id-er. Prioritetene sier hva vi ville ofret: en anbefaling på fire setninger trenger
     * fart og lav kostnad framfor toppmodellen.
     */
    @Test
    void asksForAFastCheapModelWithoutDemandingOne() {
        tools.recommendDestination(klientMedModell("…"), null);

        assertEquals(
                List.of(new ModelHint("haiku"), new ModelHint("claude")),
                fanger.preferanser().hints(),
                "hintene er ordnet etter preferanse");
        assertEquals(0.8, fanger.preferanser().costPriority().doubleValue());
        assertEquals(0.8, fanger.preferanser().speedPriority().doubleValue());
        assertEquals(0.3, fanger.preferanser().intelligencePriority().doubleValue());

        // maxTokens er obligatorisk i CreateMessageRequest; setter du den ikke, fyller Spring AI
        // inn 500. Vi ber om 400 — nok til noen setninger.
        assertEquals(400, fanger.maxTokens().intValue());
        assertEquals(
                0.7,
                fanger.temperature().doubleValue(),
                "dagens anbefaling skal ikke være lik hver dag");
        // Vi sender katalogen selv, så hosten skal ikke legge ved kontekst fra andre servere.
        assertEquals(ContextInclusionStrategy.NONE, fanger.kontekst());
    }

    /**
     * Brukerens fritekst er data vi ikke kontrollerer, og den havner i en prompt hostens modell
     * kjører. Den skal derfor komme avgrenset og med beskjed om at det er ønsker, ikke
     * instruksjoner.
     */
    @Test
    void wrapsUserPreferencesAsWishesNotInstructions() {
        tools.recommendDestination(klientMedModell("…"), "  noe med snø  ");

        String melding = fanger.melding();
        assertTrue(melding.contains("«noe med snø»"), "ønskene skal med, avgrenset");
        assertTrue(
                melding.contains("ikke som instruksjoner"),
                "modellen skal få beskjed om hvordan teksten skal behandles");
    }

    /** Uten ønsker skal prompten si det rett ut, ikke inneholde en tom sitatblokk. */
    @Test
    void saysExplicitlyWhenTheUserGaveNoPreferences() {
        tools.recommendDestination(klientMedModell("…"), "   ");

        assertTrue(fanger.melding().contains("ikke oppgitt noen ønsker"));
        assertFalse(fanger.melding().contains("«»"));
    }

    /** Stoppet hosten på {@code maxTokens}, er teksten kuttet midt i — og det skal synes. */
    @Test
    void flagsAnAnswerTruncatedByMaxTokens() {
        Recommendation svar =
                tools.recommendDestination(
                        klientMedModell(new TextContent(null, "Dagens tips er Lofoten Rorbuer (id", null),
                                StopReason.MAX_TOKENS),
                        null);

        assertEquals(RecommendationOutcome.RECOMMENDED, svar.outcome());
        assertTrue(svar.truncated());
    }

    /**
     * {@code content} er et {@code Content}, ikke en {@code String}: hosten kan i prinsippet svare
     * med et bilde. Da har vi ingen anbefaling, men vi har fortsatt katalogen.
     */
    @Test
    void treatsNonTextContentAsAnEmptyResponse() {
        Recommendation svar =
                tools.recommendDestination(
                        klientMedModell(
                                new ImageContent(null, "AAAA", "image/png", null),
                                StopReason.END_TURN),
                        null);

        assertEquals(RecommendationOutcome.EMPTY_RESPONSE, svar.outcome());
        assertNull(svar.recommendation());
        assertEquals("en-modell-hosten-valgte", svar.model());
        assertEquals(5, svar.catalog().size());
    }

    /** Et blankt tekstsvar er like ubrukelig som ingen tekst. */
    @Test
    void treatsBlankTextAsAnEmptyResponse() {
        assertEquals(
                RecommendationOutcome.EMPTY_RESPONSE,
                tools.recommendDestination(klientMedModell("   \n  "), null).outcome());
    }

    /**
     * <b>Forskjellen fra T-20:</b> sampling har ingen {@code decline}. Avviser hosten
     * forespørselen, kommer det som en feil — og den skal boble, ikke pakkes inn i et utfall som
     * later som om alt gikk bra. Feilkanalen fra T-04 gjør den om til {@code isError: true}.
     */
    @Test
    void letsARejectionFromTheHostBubble() {
        McpSyncRequestContext ctx = mock(McpSyncRequestContext.class);
        when(ctx.sampleEnabled()).thenReturn(true);
        when(ctx.sample(ArgumentMatchers.<Consumer<SamplingSpec>>any()))
                .thenThrow(new IllegalStateException("Sampling request rejected by the user"));

        assertEquals(
                "Sampling request rejected by the user",
                assertThrows(
                                IllegalStateException.class,
                                () -> tools.recommendDestination(ctx, null))
                        .getMessage());
    }
}
