package no.computas.vacationmcp.tools;

import java.time.LocalDate;
import java.util.List;
import no.computas.vacationmcp.domain.Availability;
import no.computas.vacationmcp.repository.AvailabilityRepository;
import no.computas.vacationmcp.service.ValidationException;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP-verktøy for å se hvilke perioder et reisemål er ledig i (Epic 2 i {@code BACKLOG.md}).
 *
 * <h2>Datoer over MCP-grensen</h2>
 *
 * <p>Parametrene er {@link LocalDate}, ikke {@code String}. Spring AI utleder da et helt
 * presist delskjema — {@code {"type":"string","format":"date"}} — som er den standard
 * JSON Schema-måten å si «ISO-8601-dato» på, og Jackson deserialiserer {@code "2026-07-01"}
 * rett til en {@code LocalDate}. Begge deler er verifisert gjennom protokollen; se
 * T-05-seksjonen i {@code SOLUTION-STATUS.md} for den faktiske JSON-en.
 *
 * <p>Konsekvensen er at <b>ISO-formatvalideringen skjer før metoden kalles</b>: en ugyldig
 * datostreng blir aldri en {@code LocalDate}, og Spring AI svarer med {@code isError: true}
 * og teksten «Conversion from JSON to java.time.LocalDate failed …». Valideringen som blir
 * igjen til dette laget er derfor bare regelen skjemaet ikke kan uttrykke: {@code from < to}.
 *
 * <p>Dette er den ene oppgaven der litt validering hører hjemme i verktøylaget — det finnes
 * ingen tjeneste å delegere til, siden {@link AvailabilityRepository} er et rent
 * dataaksesslag. Vi bruker {@link ValidationException} med samme melding som
 * {@code PricingService}, så modellen møter samme feilspråk uansett hvilket verktøy den traff.
 *
 * <p><b>Feil fanges ikke her.</b> Exception-en får boble ut av verktøymetoden; Spring AI gjør
 * den om til et {@code CallToolResult} med {@code isError: true} (mønsteret fra T-04).
 */
@Component
public class AvailabilityTools {

    private final AvailabilityRepository availability;

    public AvailabilityTools(AvailabilityRepository availability) {
        this.availability = availability;
    }

    /**
     * Svaret på et tilgjengelighetsoppslag.
     *
     * <p>Lista pakkes inn fordi et bart {@code []} er tvetydig for en modell: den ser ikke
     * forskjell på «ingen ledige perioder» og «verktøyet feilet» eller «jeg spurte om feil
     * datoer». Konvolutten gjentar hva det faktisk ble spurt om og teller treffene, slik at
     * tomt resultat leses som et gyldig svar. Ingen formatering eller regler her — bare
     * kontekst rundt {@code periods}.
     *
     * @param periods overlappende perioder, sortert på startdato. {@code seasonPrice = null}
     *                betyr at reisemålets ordinære pris per natt gjelder.
     */
    public record AvailabilityResult(
            long destinationId,
            LocalDate from,
            LocalDate to,
            int matchingPeriods,
            List<Availability> periods) {
    }

    @McpTool(
            name = "check_availability",
            title = "Sjekk tilgjengelighet",
            description =
                    """
                    Viser hvilke tilgjengelighetsperioder et reisemål har som overlapper \
                    datointervallet du spør om, med kapasitet (maks antall reisende i \
                    perioden) og eventuell sesongpris per natt. Bruk verktøyet når brukeren \
                    spør «er det ledig?», vil vite når på året et reisemål er åpent, eller \
                    før du foreslår konkrete datoer. Id-en får du fra `list_destinations` \
                    eller `search_destinations`.

                    Svaret gjentar spørringen (`destinationId`, `from`, `to`), teller \
                    treffene i `matchingPeriods` og lister dem i `periods`. \
                    `matchingPeriods: 0` med tom `periods` er et **gyldig svar**, ikke en \
                    feil: det betyr at reisemålet ikke har noen åpen periode i det \
                    intervallet — foreslå andre datoer i stedet for å si at noe gikk galt. \
                    Merk at en ukjent `destinationId` også gir tomt svar, så sjekk id-en mot \
                    reisemåls-verktøyene hvis du er i tvil.

                    En treffende periode betyr bare at periodene **overlapper**, ikke at \
                    hele oppholdet er dekket eller at det er plass igjen. `seasonPrice` er \
                    null når reisemålets ordinære pris per natt gjelder. Bruk `get_quote` \
                    for å få bekreftet at datoene faktisk kan bookes og hva de koster.""",
            annotations =
                    @McpTool.McpAnnotations(
                            title = "Sjekk tilgjengelighet",
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    public AvailabilityResult checkAvailability(
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Id-en til reisemålet, slik den kommer fra \
                                    `list_destinations` eller `search_destinations`.""")
                    long destinationId,
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Ønsket startdato (innsjekk) på ISO-8601-formatet \
                                    yyyy-MM-dd, f.eks. «2026-07-01». Må være før til-datoen. \
                                    Andre skrivemåter, som «01.07.2026» eller «i morgen», \
                                    avvises — regn om til en konkret dato først.""")
                    LocalDate from,
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Ønsket sluttdato (utsjekk) på ISO-8601-formatet \
                                    yyyy-MM-dd, f.eks. «2026-07-10». Må være etter \
                                    fra-datoen; datoen regnes som utsjekksdag, så et opphold \
                                    fra 1. til 10. er ni netter.""")
                    LocalDate to) {
        // ISO-formatet er allerede håndhevet av deserialiseringen (se klassekommentaren) —
        // her gjenstår bare regelen skjemaet ikke kan uttrykke. Null-sjekken er defensiv:
        // skjemavalideringen stopper et manglende argument, men meldingen blir vår.
        if (from == null || to == null) {
            throw new ValidationException("fra- og til-dato må oppgis");
        }
        if (!from.isBefore(to)) {
            throw new ValidationException("fra-dato må være før til-dato");
        }

        List<Availability> perioder = availability.findOverlapping(destinationId, from, to);
        return new AvailabilityResult(destinationId, from, to, perioder.size(), perioder);
    }
}
