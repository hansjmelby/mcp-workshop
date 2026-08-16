package no.computas.vacationmcp.resources;

import java.util.List;
import no.computas.vacationmcp.domain.Destination;
import no.computas.vacationmcp.repository.DestinationRepository;
import no.computas.vacationmcp.service.DestinationService;
import no.computas.vacationmcp.service.NotFoundException;
import no.computas.vacationmcp.service.ValidationException;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

/**
 * MCP-<b>ressurser</b> for reisemål (T-13 i {@code BACKLOG.md}).
 *
 * <p><b>Hvorfor en egen pakke ved siden av {@code tools/}?</b> Fordi dette er en annen primitiv
 * med en annen bruker. Et <em>verktøy</em> velges av <em>modellen</em>: den ser {@code inputSchema},
 * bestemmer seg for å kalle det, og får svaret tilbake midt i sin egen resonneringsløkke. En
 * <em>ressurs</em> velges typisk av <em>applikasjonen eller mennesket</em>: hosten lister dem
 * opp (som «@-nevninger», vedlegg eller en kontekstmeny), brukeren peker på én, og innholdet
 * legges <em>rått inn i konteksten</em> før modellen i det hele tatt har sagt noe. Ressursen er
 * altså lesestoff, ikke en handling — den har ingen argumenter og ingen bivirkninger, bare en
 * URI og et innhold.
 *
 * <p>Det praktiske utslaget her: samme data som {@code list_destinations} eksponeres én gang
 * til, men formatert for å <em>leses</em> (markdown), ikke for å parses videre i en verktøykjede
 * (JSON). Se «Ressurser over MCP-grensen» i {@code SOLUTION-STATUS.md} for begrunnelsen.
 *
 * <p>Klassen er fortsatt en fasade — den henter data fra {@code service}/{@code repository} og
 * formaterer. Ingen SQL og ingen forretningsregler.
 *
 * <p><b>Merk:</b> {@code @McpResource} har også {@code title()} og {@code annotations()}, men
 * {@code SyncMcpResourceProvider} i Spring AI 2.0.0 leser dem ikke — bare {@code uri},
 * {@code name}, {@code description}, {@code mimeType} og {@code meta} havner i
 * {@code resources/list}. Derfor er de ikke satt her; det klienten skal vise, står i
 * {@code name} og {@code description}.
 */
@Component
public class DestinationResources {

    private final DestinationService destinations;

    /**
     * {@link DestinationService} har ingen oppslagsmetode på id, så enkeltoppslaget går rett på
     * repository-et. Det er lov ifølge {@code CLAUDE.md} («lavnivå ved behov») — men merk at
     * {@code findById} <em>ikke</em> filtrerer på {@code available}, i motsetning til
     * {@code listAvailable()}. Ressursen sier derfor eksplisitt om reisemålet er åpent for
     * booking.
     */
    private final DestinationRepository repository;

    public DestinationResources(DestinationService destinations, DestinationRepository repository) {
        this.destinations = destinations;
        this.repository = repository;
    }

    /**
     * <b>Statisk ressurs.</b> URI-en har ingen {@code {variabler}}, så Spring AI legger den i
     * {@code resources/list}. Metoden er parameterløs.
     */
    @McpResource(
            uri = "destination://catalog",
            name = "destination_catalog",
            description =
                    """
                    Hele katalogen over feriereisemål som er åpne for booking, som lesbar \
                    markdown: navn, id, land, pris per natt og en kort beskrivelse. Legg denne \
                    i konteksten når samtalen skal handle om «hva finnes?» — da slipper \
                    modellen å kalle `list_destinations` først. Bruk verktøyene når du trenger \
                    et filtrert eller ferskt oppslag.""",
            mimeType = "text/markdown")
    public String destinationCatalog() {
        List<Destination> katalog = destinations.listAvailable();
        if (katalog.isEmpty()) {
            return "# Reisemålskatalog\n\nIngen reisemål er åpne for booking akkurat nå.\n";
        }

        StringBuilder markdown = new StringBuilder("# Reisemålskatalog\n\n");
        markdown.append("Katalogen inneholder ")
                .append(katalog.size())
                .append(" reisemål (åpne for booking). Prisen er utgangspris per natt i norske ")
                .append("kroner — for en konkret periode kan sesongpris gjelde, så bruk verktøyet ")
                .append("`get_quote` før du oppgir en totalsum.\n\n");
        for (Destination reisemaal : katalog) {
            markdown.append("- **")
                    .append(reisemaal.name())
                    .append("** (id ")
                    .append(reisemaal.id())
                    .append(") — ")
                    .append(reisemaal.country())
                    .append(", ")
                    .append(kroner(reisemaal.pricePerNight()))
                    .append(" kr per natt. ")
                    .append(reisemaal.description())
                    .append("\n");
        }
        return markdown.toString();
    }

    /**
     * <b>Resource template.</b> URI-en inneholder {@code {id}}, og da havner oppføringen i
     * {@code resources/templates/list} i stedet — en mal klienten fyller inn selv, ikke en
     * ferdig liste den kan bla i.
     *
     * <p><b>URI-variabler kommer alltid som {@link String}.</b> Spring AI krever det
     * ({@code AbstractMcpResourceMethodCallback.validateParametersWithUriVariables}: «URI
     * variable parameters must be of type String»), så konverteringen til {@code long} er vår
     * jobb — det finnes ikke noe {@code inputSchema} som kan validere den slik det gjør for
     * verktøyparametere.
     *
     * <p><b>Ukjent id lar vi boble</b>, akkurat som i {@code tools/}-laget — men utfallet er et
     * annet: {@code resources/read} har ingen {@code isError}-kanal, så klienten får en ekte
     * JSON-RPC-{@code error} ({@code -32602}) med meldingen vår i {@code data}. Meldingene her er
     * derfor det eneste vi styrer, og de er skrevet for å være til hjelp. Se
     * {@code SOLUTION-STATUS.md} (T-13) for tracen og for hvorfor det <em>ikke</em> nytter å
     * kaste {@code McpError.RESOURCE_NOT_FOUND} selv i Spring AI 2.0.0.
     */
    @McpResource(
            uri = "destination://{id}",
            name = "destination",
            description =
                    """
                    Ett enkelt reisemål slått opp på id, som lesbar markdown: navn, land, pris \
                    per natt, beskrivelse og om det er åpent for booking. Fyll inn id-en fra \
                    `destination://catalog` eller fra et verktøysvar — f.eks. \
                    `destination://3`. Ukjent id gir en feil, ikke et tomt innhold.""",
            mimeType = "text/markdown")
    public String destination(String id) {
        long reisemaalId = parseId(id);
        Destination reisemaal = repository
                .findById(reisemaalId)
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        "Fant ikke reisemål med id %d. Gyldige id-er står i destination://catalog."
                                                .formatted(reisemaalId)));

        return "# %s\n\n".formatted(reisemaal.name())
                + "- **id:** %d — bruk denne i `check_availability`, `get_quote` og `create_booking`.\n"
                        .formatted(reisemaal.id())
                + "- **Land:** %s\n".formatted(reisemaal.country())
                + "- **Pris per natt:** %s kr (utgangspris; sesongpris kan gjelde for en konkret periode)\n"
                        .formatted(kroner(reisemaal.pricePerNight()))
                + "- **Åpent for booking:** %s\n\n".formatted(reisemaal.available() ? "ja" : "nei")
                + reisemaal.description()
                + "\n";
    }

    /**
     * URI-malen slipper gjennom hva som helst på {@code {id}}-plassen — {@code destination://abc}
     * havner her like fullt. Det finnes ikke noe {@code inputSchema} som stopper det slik det
     * gjør for verktøyparametere, så konverteringen må vi validere selv.
     */
    private static long parseId(String id) {
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new ValidationException(
                    "«%s» er ikke en gyldig reisemål-id. URI-malen er destination://{id} der {id} er et heltall, f.eks. destination://3."
                            .formatted(id));
        }
    }

    /** 1850.0 → «1850», 1850.5 → «1850.5». Ren presentasjon — hører hjemme i ressurslaget. */
    private static String kroner(double beloep) {
        return beloep == Math.rint(beloep) ? Long.toString((long) beloep) : Double.toString(beloep);
    }
}
