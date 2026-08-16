package no.computas.vacationmcp.tools;

import java.util.List;
import no.computas.vacationmcp.domain.Destination;
import no.computas.vacationmcp.service.DestinationService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP-verktøy for å utforske reisemål (Epic 1 i {@code BACKLOG.md}).
 *
 * <p>Klassen er en ren fasade: den tar imot kallet fra MCP-klienten og delegerer rett
 * videre til {@link DestinationService}. Ingen SQL og ingen forretningsregler her.
 *
 * <p>Returverdien er domene-recorden {@link Destination}. Spring AI serialiserer alt som
 * ikke er {@code String} til JSON i tekstblokken av {@code CallToolResult}, så modellen
 * får feltnavn og verdier i par — inkludert {@code id}-en den trenger for å kalle
 * tilgjengelighets-, pris- og bookingverktøyene senere.
 *
 * <p><b>Feil fanges ikke her.</b> {@code ValidationException} fra tjenestelaget får boble ut
 * av verktøymetoden; Spring AI fanger den i {@code SyncMcpToolMethodCallback.apply(...)} og
 * gjør den om til et {@code CallToolResult} med {@code isError: true} og feilmeldingen som
 * tekst — ingen stacktrace når klienten. Se «Feilhåndtering» i {@code SOLUTION-STATUS.md}.
 */
@Component
public class DestinationTools {

    private final DestinationService destinations;

    public DestinationTools(DestinationService destinations) {
        this.destinations = destinations;
    }

    @McpTool(
            name = "list_destinations",
            title = "Tilgjengelige reisemål",
            description =
                    """
                    Lister alle feriereisemål som er åpne for booking, med id, navn, land, \
                    kort beskrivelse og pris per natt i norske kroner. Bruk verktøyet når \
                    brukeren vil se hele utvalget, spør «hvor kan jeg reise?», eller når du \
                    trenger id-en til et reisemål for å gå videre til tilgjengelighet, pris \
                    eller booking. Verktøyet tar ingen argumenter og returnerer alltid hele \
                    lista — skal du filtrere på tema, land eller pristak, bruk \
                    `search_destinations` i stedet. Prisen per natt er utgangsprisen — for en \
                    konkret periode kan sesongpris gjelde, så bruk pris-verktøyet før du \
                    oppgir en totalsum.""",
            annotations =
                    @McpTool.McpAnnotations(
                            title = "Tilgjengelige reisemål",
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    public List<Destination> listDestinations() {
        return destinations.listAvailable();
    }

    /**
     * Alle tre parametrene er valgfrie i {@link DestinationService#search}, og må derfor
     * merkes {@code @McpToolParam(required = false)} — Spring AI gjør parametere
     * <em>obligatoriske</em> som default ({@code PROPERTY_REQUIRED_BY_DEFAULT = true}), så
     * uten flagget havner de i {@code required}-lista i {@code inputSchema}.
     *
     * <p>{@code maxPricePerNight} er en bokset {@link Double}, ikke {@code double}: «ikke
     * oppgitt» skal komme fram som {@code null} til tjenesten. Med primitiv type ville et
     * utelatt argument kollapset til {@code 0.0} og filtrert bort alt.
     */
    @McpTool(
            name = "search_destinations",
            title = "Søk i reisemål",
            description =
                    """
                    Søker blant feriereisemålene som er åpne for booking og returnerer de \
                    samme feltene som `list_destinations` (id, navn, land, beskrivelse, pris \
                    per natt i norske kroner). Bruk dette verktøyet når brukeren har et \
                    kriterium å filtrere på — et tema («nordlys», «vingård»), et bestemt \
                    land, eller et pristak — og bruk `list_destinations` når hele utvalget \
                    skal vises. Alle tre argumentene er valgfrie og kombineres med OG; \
                    utelater du alle, gir søket samme resultat som `list_destinations`. Et \
                    tomt resultat betyr at ingenting matchet: prøv et bredere søk (færre \
                    filtre) før du sier at reisemålet ikke finnes. Prisen per natt er \
                    utgangsprisen — sesongpris kan gjelde for en konkret periode, så bruk \
                    pris-verktøyet før du oppgir en totalsum.""",
            annotations =
                    @McpTool.McpAnnotations(
                            title = "Søk i reisemål",
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    public List<Destination> searchDestinations(
            @McpToolParam(
                            required = false,
                            description =
                                    """
                                    Fritekst som må forekomme i navnet eller beskrivelsen til \
                                    reisemålet, f.eks. «nordlys», «rorbu» eller «vingård». \
                                    Delvis treff holder. Bruk ett stikkord om gangen — hele \
                                    setninger matcher sjelden. Utelat for å ikke filtrere på \
                                    tekst.""")
                    String query,
            @McpToolParam(
                            required = false,
                            description =
                                    """
                                    Land, skrevet nøyaktig slik det står i dataene (norsk \
                                    landnavn, f.eks. «Norge», «Hellas», «Japan», «Italia»). \
                                    Dette er et eksakt treff, ikke et delvis søk — er du \
                                    usikker på skrivemåten, kall `list_destinations` først. \
                                    Utelat for å søke i alle land.""")
                    String country,
            @McpToolParam(
                            required = false,
                            description =
                                    """
                                    Øvre grense for pris per natt i norske kroner; bare \
                                    reisemål med pris lik eller lavere kommer med. Må være \
                                    null eller positiv — et negativt tall avvises som feil. \
                                    Utelat for å ikke filtrere på pris.""")
                    Double maxPricePerNight) {
        return destinations.search(query, country, maxPricePerNight);
    }
}
