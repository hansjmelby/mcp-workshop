package no.computas.vacationmcp.tools;

import java.time.LocalDate;
import no.computas.vacationmcp.service.PricingService;
import no.computas.vacationmcp.service.Quote;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP-verktøy for pristilbud (Epic 2 i {@code BACKLOG.md}).
 *
 * <p>Klassen er en ren fasade mot {@link PricingService#quote}. Tjenesten gjør <em>alt</em>:
 * validerer datoer og antall reisende, slår opp reisemålet, finner perioden som dekker
 * oppholdet, og regner ut prisen som (sesongpris ?: reisemålets pris per natt) × netter ×
 * reisende. Verktøyet legger ikke en eneste regel eller utregning oppå det.
 *
 * <p><b>Ingen mapping av svaret.</b> {@link Quote} inneholder allerede hvert ledd i
 * regnestykket — {@code nights}, {@code numTravelers}, {@code pricePerNight} (prisen som
 * faktisk ble brukt) og {@code totalPrice} — pluss hele {@code destination}-recorden, slik at
 * modellen ser reisemålets <em>ordinære</em> {@code pricePerNight} ved siden av den som ble
 * brukt. Er de to ulike, var det sesongpris; er de like, gjaldt normalprisen. Modellen kan
 * altså forklare summen linje for linje uten flere verktøykall, og recorden returneres derfor
 * direkte etter T-03-konvensjonen (domene-record inn i tekstblokken som JSON) i stedet for at
 * verktøyet finner opp en egen konvolutt.
 *
 * <p>Datoene er {@link LocalDate} etter den felles beslutningen fra T-05, og
 * {@code numTravelers} er en primitiv {@code int}: verdien er obligatorisk, så det finnes
 * ingen «ikke oppgitt» som må overleve som {@code null} (motsatt {@code Double} i
 * {@code search_destinations}). Primitiven gir også det strammeste skjemaet —
 * {@code {"type":"integer","format":"int32"}} i {@code required}.
 *
 * <p><b>Feil fanges ikke her.</b> {@code ValidationException} (ugyldige datoer,
 * {@code numTravelers < 1}, ingen dekkende periode) og {@code NotFoundException} (ukjent
 * reisemål) får boble ut av verktøymetoden; Spring AI gjør dem om til et
 * {@code CallToolResult} med {@code isError: true} og meldingen som tekst. Mønsteret er
 * etablert i T-04 — se {@code SOLUTION-STATUS.md}.
 */
@Component
public class PricingTools {

    private final PricingService pricing;

    public PricingTools(PricingService pricing) {
        this.pricing = pricing;
    }

    @McpTool(
            name = "get_quote",
            title = "Beregn pris",
            description =
                    """
                    Beregner totalprisen for et konkret opphold: et reisemål, et \
                    datointervall og et antall reisende. Bruk verktøyet så snart brukeren \
                    spør «hva koster det?» eller før du oppgir en pris i svaret ditt — \
                    prisen per natt fra `list_destinations`/`search_destinations` er bare \
                    utgangsprisen, og sesongpris kan gjelde for perioden.

                    Svaret inneholder hele regnestykket: `nights` (antall netter), \
                    `numTravelers`, `pricePerNight` (prisen som faktisk ble brukt) og \
                    `totalPrice` = pricePerNight × nights × numTravelers, i norske kroner. \
                    Under `destination` ligger reisemålet med sin ordinære pris per natt — er \
                    den ulik `pricePerNight`, ble det brukt sesongpris, og det er verdt å \
                    nevne for brukeren.

                    Verktøyet krever at **én** tilgjengelighetsperiode dekker hele oppholdet. \
                    Datoer utenfor en slik periode avvises med «Ingen tilgjengelig periode \
                    dekker …» — det gjelder også et opphold som strekker seg over to \
                    tilstøtende perioder. Kall `check_availability` for å se hvilke perioder \
                    reisemålet faktisk har, og foreslå datoer innenfor én av dem. Verktøyet \
                    reserverer ingenting og sjekker ikke om det er plass igjen; det gjør \
                    `create_booking`.""",
            annotations =
                    @McpTool.McpAnnotations(
                            title = "Beregn pris",
                            readOnlyHint = true,
                            destructiveHint = false,
                            idempotentHint = true,
                            openWorldHint = false))
    public Quote getQuote(
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Id-en til reisemålet, slik den kommer fra \
                                    `list_destinations` eller `search_destinations`. En ukjent \
                                    id gir en feil, ikke et tomt svar.""")
                    long destinationId,
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Innsjekksdato på ISO-8601-formatet yyyy-MM-dd, f.eks. \
                                    «2026-07-01». Må være før til-datoen. Andre skrivemåter, \
                                    som «01.07.2026» eller «i morgen», avvises — regn om til \
                                    en konkret dato først.""")
                    LocalDate from,
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Utsjekksdato på ISO-8601-formatet yyyy-MM-dd, f.eks. \
                                    «2026-07-10». Må være etter fra-datoen. Datoen regnes som \
                                    utsjekksdag og faktureres ikke, så et opphold fra 1. til \
                                    10. er ni netter.""")
                    LocalDate to,
            @McpToolParam(
                            required = true,
                            description =
                                    """
                                    Antall reisende, minst 1. Prisen ganges opp per reisende, \
                                    så tallet påvirker totalsummen direkte — spør brukeren \
                                    hvis det ikke er oppgitt, ikke gjett. 0 eller et negativt \
                                    tall avvises som feil.""")
                    int numTravelers) {
        // Ingen validering og ingen utregning her: PricingService.quote(...) gjør begge deler,
        // og kaster ValidationException/NotFoundException som får boble videre (T-04).
        return pricing.quote(destinationId, from, to, numTravelers);
    }
}
