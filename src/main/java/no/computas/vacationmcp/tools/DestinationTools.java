package no.computas.vacationmcp.tools;

import java.util.List;
import no.computas.vacationmcp.domain.Destination;
import no.computas.vacationmcp.service.DestinationService;
import org.springframework.ai.mcp.annotation.McpTool;
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
                    lista. Prisen per natt er utgangsprisen — for en konkret periode kan \
                    sesongpris gjelde, så bruk pris-verktøyet før du oppgir en totalsum.""",
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
}
