package no.computas.vacationmcp.prompts;

import io.modelcontextprotocol.spec.McpSchema.EmbeddedResource;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import no.computas.vacationmcp.resources.BookingResources;
import no.computas.vacationmcp.service.ValidationException;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

/**
 * MCP-<b>prompts</b> (T-15 i {@code BACKLOG.md}) — det tredje og siste primitivet i Epic 5.
 *
 * <p><b>Hvem styrer hva?</b> Det er hele poenget med å ha tre primitiver i stedet for ett:
 *
 * <ul>
 *   <li><b>Verktøy</b> ({@code tools/}) er <em>modellstyrt</em>. Modellen leser {@code inputSchema}
 *       og bestemmer selv, midt i resonneringen, om og når den skal kalle noe.
 *   <li><b>Ressurser</b> ({@code resources/}) er <em>applikasjonsstyrt</em>. Hosten lister dem opp,
 *       og applikasjonen eller mennesket bestemmer hvilket innhold som legges i konteksten.
 *   <li><b>Prompts</b> (denne pakka) er <em>brukerstyrt</em>. De dukker opp i en <em>meny</em> i
 *       hosten — typisk slash-kommandoer — og skjer bare fordi brukeren aktivt valgte dem. En
 *       modell velger aldri en prompt, og en applikasjon legger den aldri ved automatisk.
 * </ul>
 *
 * <p>Det praktiske utslaget: en prompt er en <b>ferdigskrevet melding</b> vi legger i munnen på
 * brukeren, ikke et svar og ikke et datasett. Derfor er innholdet her en <em>arbeidsflyt uttrykt
 * som tekst</em> ({@code plan_vacation_within_budget}) eller en instruksjon med vedlagt kontekst
 * ({@code travel_summary}) — og derfor har begge {@link Role#USER} på meldingene sine.
 *
 * <p><b>Hvorfor bare én klasse, når {@code tools/} har én per domeneområde?</b> Fordi en prompt
 * ikke <em>har</em> ett domeneområde. {@code plan_vacation_within_budget} er en kjede tvers
 * gjennom reisemål, tilgjengelighet, pris og booking — den ville ikke passet i noen av
 * {@code DestinationTools}/{@code PricingTools}/{@code BookingTools}. Prompts er workflows, og
 * workflows går på tvers.
 *
 * <p><b>Tre ting Spring AI 2.0.0 gjør (og ikke gjør) som er verdt å kjenne til.</b> Alle tre er
 * lest av kilden og verifisert mot ekte JSON — se T-15-seksjonen i {@code SOLUTION-STATUS.md}:
 *
 * <ol>
 *   <li><b>Argumenter beskrives med {@link McpArg}</b>, ikke med {@code @McpToolParam}. Uten
 *       annotasjonen får argumentet beskrivelsen «Parameter of type String» og
 *       {@code required: false} ({@code PromptAdapter.extractPromptArguments}). Merk at
 *       {@code McpArg.required()} har default <b>false</b> — motsatt av {@code @McpToolParam}.
 *   <li><b>{@code required} håndheves ikke av noen.</b> Det finnes ikke noe {@code inputSchema}
 *       for prompts — bare en flat liste med navn, beskrivelse og et {@code required}-flagg som er
 *       ren dokumentasjon. Et manglende argument blir {@code null} i metoden
 *       ({@code AbstractMcpPromptMethodCallback.buildArgs}), så <em>valideringen er vår</em>. Derfor
 *       er alle argumentene her boksede typer ({@code Double}, {@code Integer}, {@code String}) —
 *       en primitiv {@code double} ville gitt en uleselig refleksjonsfeil på et manglende argument.
 *   <li><b>Returtypen bestemmer rollen.</b> Returnerer du en {@code String}, pakkes den inn som en
 *       melding med rollen <b>{@code assistant}</b> — altså som om modellen allerede hadde sagt
 *       det. Det er nesten aldri det du vil ha for en promptmal. Vi returnerer derfor
 *       {@link GetPromptResult} og setter {@link Role#USER} selv.
 * </ol>
 */
@Component
public class VacationPrompts {

    /**
     * Prompten {@code travel_summary} gjenbruker markdown-en fra {@code booking://{id}} i stedet
     * for å formatere bookingen på nytt. Se metoden for begrunnelsen.
     */
    private final BookingResources bookingResources;

    public VacationPrompts(BookingResources bookingResources) {
        this.bookingResources = bookingResources;
    }

    // --- Prompt 1: en arbeidsflyt uttrykt som tekst -----------------------------------------

    /**
     * «Planlegg en ferie innen budsjett» — en <b>arbeidsflyt</b>, ikke et datauttrekk.
     *
     * <p>Denne prompten kaller ingen tjenester og henter ingen data. Den skriver ut den
     * rekkefølgen verktøyene fra T-03 til T-07 skal brukes i, og hvorfor: søk fram kandidater,
     * sjekk ledighet, hent ekte pris med {@code get_quote} (utgangsprisen i katalogen er ikke
     * fasit — sesongpris kan gjelde), sammenlign mot budsjettet, og book <em>først</em> når
     * brukeren har sagt ja.
     *
     * <p>Nettopp derfor er en prompt riktig primitiv her. Rekkefølgen er ikke noe modellen kan lese
     * ut av {@code tools/list}: hvert verktøy beskriver seg selv, men ingen av dem sier «kall meg
     * etter `check_availability` og før `create_booking`». Å presse den kunnskapen inn i
     * {@code description}-feltene ville gjort katalogen full av instruksjoner som bare gjelder ett
     * bruksmønster. Som en prompt er den valgfri, navngitt, og synlig i hostens meny.
     *
     * <p>Den eneste utregningen her er en <em>hjelp</em> til modellen: er perioden oppgitt, kan vi
     * regne budsjettet om til en øvre pris per natt, som er akkurat det
     * {@code search_destinations} tar som filter. Det er presentasjonslogikk, ikke en
     * forretningsregel — den ekte prisen kommer uansett fra {@code get_quote}.
     */
    @McpPrompt(
            name = "plan_vacation_within_budget",
            title = "Planlegg ferie innen budsjett",
            description =
                    """
                    Ferdig arbeidsflyt for å finne og booke en ferie innenfor et samlet \
                    budsjett. Fyller ut rekkefølgen verktøyene skal brukes i — søk, ledighet, \
                    pris, sammenligning, booking — slik at prisen som presenteres er den \
                    faktiske og ikke utgangsprisen i katalogen. Land og periode er valgfrie; \
                    oppgir du periode, regnes budsjettet også om til en øvre pris per natt.""")
    public GetPromptResult planVacationWithinBudget(
            @McpArg(
                            name = "budget",
                            required = true,
                            description =
                                    "Samlet budsjett for hele ferien i norske kroner, f.eks. 45000. Dette er totalsummen for alle reisende og alle netter, ikke prisen per natt.")
                    Double budget,
            @McpArg(
                            name = "numTravelers",
                            required = true,
                            description =
                                    "Antall reisende, minst 1. Prisen ganges opp per reisende, så tallet påvirker totalsummen direkte.")
                    Integer numTravelers,
            @McpArg(
                            name = "country",
                            description =
                                    "Valgfritt land å begrense søket til, skrevet slik det står i katalogen (norsk landnavn, f.eks. «Norge», «Hellas», «Japan», «Italia»). Utelat for å søke i alle land.")
                    String country,
            @McpArg(
                            name = "from",
                            description =
                                    "Valgfri innsjekksdato, ISO-8601 (yyyy-MM-dd), f.eks. «2026-07-01». Må oppgis sammen med «to».")
                    String from,
            @McpArg(
                            name = "to",
                            description =
                                    "Valgfri utsjekksdato, ISO-8601 (yyyy-MM-dd), f.eks. «2026-07-08». Utsjekksdagen faktureres ikke, så 01. til 08. er sju netter. Må oppgis sammen med «from».")
                    String to) {

        // required=true i @McpArg er dokumentasjon, ikke en kontrakt noen håndhever — et
        // manglende argument kommer hit som null. Valideringen er derfor vår, og meldingene
        // skrives for mennesket/klienten som ser JSON-RPC-feilen (samme kanal som resources).
        double budsjett = kreverPositivt(budget);
        int reisende = kreverMinstEnReisende(numTravelers);
        Periode periode = Periode.av(from, to);

        StringBuilder melding = new StringBuilder();
        melding.append("Jeg vil planlegge en ferie for ")
                .append(reisende)
                .append(" reisende med et samlet budsjett på ")
                .append(kroner(budsjett))
                .append(" kr.\n");
        if (country != null && !country.isBlank()) {
            melding.append("Jeg vil helst reise til ").append(country.trim()).append(".\n");
        }
        if (periode != null) {
            melding.append("Perioden er ")
                    .append(periode.from())
                    .append(" til ")
                    .append(periode.to())
                    .append(" — ")
                    .append(periode.netter())
                    .append(periode.netter() == 1 ? " natt.\n" : " netter.\n");
        } else {
            melding.append(
                    "Jeg har ikke bestemt datoer ennå — foreslå en periode ut fra hva som er ledig.\n");
        }

        melding.append(
                """

                Bruk verktøyene på denne serveren i denne rekkefølgen, og ikke gjett på priser \
                eller ledighet:

                """);

        melding.append("1. **Finn kandidater.** ");
        if (country != null && !country.isBlank()) {
            melding.append("Kall `search_destinations` med `country` = «")
                    .append(country.trim())
                    .append("»");
        } else {
            melding.append("Kall `list_destinations` for hele katalogen, eller `search_destinations`"
                    + " hvis jeg har nevnt et stikkord");
        }
        if (periode != null) {
            melding.append(" og `maxPricePerNight` = ")
                    .append(maksPrisPerNatt(budsjett, periode.netter(), reisende))
                    .append(" (budsjettet delt på ")
                    .append(periode.netter())
                    .append(periode.netter() == 1 ? " natt og " : " netter og ")
                    .append(reisende)
                    .append(reisende == 1 ? " reisende)" : " reisende)");
        }
        melding.append(
                ". Prisen du får her er en **utgangspris** per natt — den er til å sortere etter,"
                        + " ikke til å love bort.\n");

        melding.append(
                """
                2. **Sjekk ledighet.** Kall `check_availability` for hver kandidat. Verktøyet \
                krever at én sammenhengende periode dekker hele oppholdet, og `capacity` er \
                totalkapasitet — ikke ledige plasser.
                3. **Hent den ekte prisen.** Kall `get_quote` for hver kandidat som er ledig, med \
                reisemålets id, datoene og antall reisende. Svaret inneholder hele regnestykket \
                (`nights`, `pricePerNight`, `totalPrice`). Er `pricePerNight` ulik reisemålets \
                ordinære pris, gjelder sesongpris — si det til meg.
                4. **Sammenlign mot budsjettet.** Presenter de to–tre beste alternativene som \
                havner innenfor budsjettet, med totalpris og hva som skiller dem. Er ingenting \
                innenfor, si det rett ut og foreslå hva som skal justeres: kortere opphold, \
                annen periode eller et rimeligere reisemål.
                5. **Book først når jeg har sagt ja.** `create_booking` er det eneste skrivende \
                steget. Vent på en eksplisitt bekreftelse fra meg, og gjenta reisemål, datoer, \
                antall reisende og totalpris før du kaller det.

                Oppgi alltid beløp i norske kroner, og vis totalprisen — ikke bare prisen per \
                natt.""");

        return new GetPromptResult(
                "Planlegg en ferie for %d reisende innenfor %s kr"
                        .formatted(reisende, kroner(budsjett)),
                List.of(new PromptMessage(Role.USER, TextContent.builder(melding.toString()).build())),
                null);
    }

    // --- Prompt 2: instruksjon + vedlagt kontekst -------------------------------------------

    /**
     * «Reisesammendrag» — en instruksjon med bookingen <b>vedlagt</b>.
     *
     * <p><b>Designvalget oppgaven spør om: peke på {@code booking://{id}} eller gjenta innholdet?</b>
     * Svaret er «peke» — og MCP har en egen mekanisme for nettopp det. Meldingen består av to deler:
     * en tekstinstruksjon, og en {@link EmbeddedResource} som bærer med seg innholdet fra
     * {@code booking://{id}}, komplett med URI og {@code mimeType}. Markdown-en produseres av
     * {@link BookingResources#booking(String)} — den samme metoden som svarer på
     * {@code resources/read} — så formateringen finnes fortsatt bare ett sted. Duplisering unngås
     * ved gjenbruk av koden, ikke ved å utelate innholdet.
     *
     * <p>Alternativene, og hvorfor de ble valgt bort:
     *
     * <ul>
     *   <li><b>Formatere bookingen på nytt her.</b> To steder å vedlikeholde samme tekst, som
     *       garantert glir fra hverandre. Avvist.
     *   <li><b>Bare skrive «les ressursen {@code booking://7}» i teksten.</b> Da må hosten forstå at
     *       den skal gjøre et {@code resources/read} etterpå — og det gjør ikke alle. Prompten ville
     *       vært avhengig av en oppfølging vi ikke kontrollerer.
     *   <li><b>{@code ResourceLink} i stedet for {@code EmbeddedResource}.</b> Det er en ren peker
     *       (URI + navn), som har samme problem: innholdet kommer ikke med. Riktig når ressursen er
     *       stor eller brukeren skal velge; feil her, der hele poenget er å oppsummere én liten,
     *       konkret booking i én omgang.
     * </ul>
     *
     * <p>Effekten er at prompten er <em>selvbærende</em>: ett {@code prompts/get} gir hosten både
     * instruksjonen og dataene. Prisen er den samme som ressursen alltid har hatt — innholdet er et
     * øyeblikksbilde, så instruksjonen ber eksplisitt om et {@code get_booking} hvis statusen skal
     * bekreftes.
     */
    @McpPrompt(
            name = "travel_summary",
            title = "Reisesammendrag",
            description =
                    """
                    Lager et kort, kundevennlig sammendrag av én booking: reisemål, datoer, \
                    antall reisende, totalpris og hva som skjer videre. Bookingen legges ved i \
                    prompten som en `booking://{id}`-ressurs, så sammendraget kan skrives uten \
                    et eneste verktøykall. Oppgi id-en fra `create_booking` eller \
                    `list_bookings`.""")
    public GetPromptResult travelSummary(
            @McpArg(
                            name = "bookingId",
                            required = true,
                            description =
                                    "Id-en til bookingen som skal oppsummeres, f.eks. «7». Kommer fra svaret på `create_booking` eller `list_bookings`. En ukjent id gir en feil, ikke et tomt sammendrag.")
                    String bookingId) {

        // Samme mønster som for prompt 1: «required» stoppet ingenting, så vi sjekker selv.
        // Id-en tas imot som String — ikke Long — nettopp fordi vi da eier feilmeldingen.
        // Med en Long ville Spring AI kalt Long.parseLong for oss inne i
        // AbstractMcpPromptMethodCallback.convertArgumentValue, og «abc» hadde gitt klienten
        // «For input string: "abc"» i stedet for noe som peker videre til `list_bookings`.
        if (bookingId == null || bookingId.isBlank()) {
            throw new ValidationException(
                    "Argumentet «bookingId» må oppgis. Det er id-en til bookingen som skal oppsummeres — hent den fra svaret på `create_booking` eller fra `list_bookings`.");
        }

        String uri = "booking://" + bookingId.trim();
        // Kaster ValidationException på ikke-numerisk id og NotFoundException på ukjent id,
        // med de samme meldingene som resources/read gir. Vi lar dem boble (T-04-konvensjonen);
        // for prompts blir utfallet en JSON-RPC-error -32602, som for ressurser.
        String bookingMarkdown = bookingResources.booking(bookingId.trim());

        PromptMessage instruksjon =
                new PromptMessage(
                        Role.USER,
                        TextContent.builder(
                                        """
                                Skriv et kort reisesammendrag av bookingen som er lagt ved under, \
                                til kunden selv. Ta med reisemål og land, datoene med antall \
                                netter, antall reisende og totalprisen i norske kroner, og avslutt \
                                med hva som skjer videre ut fra statusen — hvilke steg som gjenstår \
                                før reisen er i boks.

                                Alt du trenger står i vedlegget, så du trenger ikke kalle noen \
                                verktøy for å skrive sammendraget. Vedlegget er et øyeblikksbilde: \
                                skal du bekrefte at statusen fortsatt stemmer, kall `get_booking` \
                                med id-en fra vedlegget først. Ikke endre noe — `create_booking`, \
                                `update_booking_status` og `cancel_booking` hører ikke hjemme her.

                                Skriv på norsk, i vanlig prosa. Ikke gjenta markdown-lista slik den \
                                står.""")
                                .build());

        PromptMessage vedlegg =
                new PromptMessage(
                        Role.USER,
                        new EmbeddedResource(
                                null,
                                new TextResourceContents(uri, "text/markdown", bookingMarkdown, null),
                                null));

        return new GetPromptResult(
                "Reisesammendrag for " + uri, List.of(instruksjon, vedlegg), null);
    }

    // --- Validering av argumenter (som ingen andre gjør for oss) -----------------------------

    private static double kreverPositivt(Double budget) {
        if (budget == null) {
            throw new ValidationException(
                    "Argumentet «budget» må oppgis. Det er det samlede budsjettet for hele ferien i norske kroner, f.eks. 45000.");
        }
        if (budget <= 0) {
            throw new ValidationException(
                    "«budget» må være et positivt beløp i norske kroner, ikke %s."
                            .formatted(kroner(budget)));
        }
        return budget;
    }

    private static int kreverMinstEnReisende(Integer numTravelers) {
        if (numTravelers == null) {
            throw new ValidationException(
                    "Argumentet «numTravelers» må oppgis. Det er antall reisende, minst 1.");
        }
        if (numTravelers < 1) {
            throw new ValidationException(
                    "«numTravelers» må være minst 1, ikke %d.".formatted(numTravelers));
        }
        return numTravelers;
    }

    /**
     * Perioden er valgfri, men den er valgfri som et <em>par</em>: én dato alene gir ingen netter å
     * regne med. Datoene tas imot som {@link String} og parses her — motsatt av
     * {@link no.computas.vacationmcp.tools.PricingTools}, som bruker {@link LocalDate} direkte.
     * Grunnen er at prompt-argumenter ikke har noe skjema og ingen Jackson-deserialisering:
     * {@code AbstractMcpPromptMethodCallback.convertArgumentValue} kjenner bare {@code String},
     * {@code Integer}, {@code Long}, {@code Double} og {@code Boolean} — alt annet sendes videre
     * uendret og feiler i refleksjonskallet. Datobeslutningen fra T-05 gjelder altså verktøy, ikke
     * prompts.
     */
    private record Periode(LocalDate from, LocalDate to, long netter) {

        static Periode av(String from, String to) {
            boolean harFra = from != null && !from.isBlank();
            boolean harTil = to != null && !to.isBlank();
            if (!harFra && !harTil) {
                return null;
            }
            if (harFra != harTil) {
                throw new ValidationException(
                        "«from» og «to» må oppgis sammen — én dato alene gir ingen periode å regne på. Utelat begge for å la modellen foreslå datoer.");
            }
            LocalDate fra = parse(from, "from");
            LocalDate til = parse(to, "to");
            if (!fra.isBefore(til)) {
                throw new ValidationException(
                        "fra-dato må være før til-dato («from» = %s, «to» = %s).".formatted(fra, til));
            }
            return new Periode(fra, til, ChronoUnit.DAYS.between(fra, til));
        }

        private static LocalDate parse(String verdi, String navn) {
            try {
                return LocalDate.parse(verdi.trim());
            } catch (DateTimeParseException e) {
                throw new ValidationException(
                        "«%s» = «%s» er ikke en gyldig dato. Bruk ISO-8601 (yyyy-MM-dd), f.eks. «2026-07-01»."
                                .formatted(navn, verdi));
            }
        }
    }

    /** Budsjettet omregnet til en øvre pris per natt — akkurat det `search_destinations` filtrerer på. */
    private static String maksPrisPerNatt(double budsjett, long netter, int reisende) {
        return Long.toString((long) Math.floor(budsjett / (netter * reisende)));
    }

    /** 45000.0 → «45000», 45000.5 → «45000.5». Ren presentasjon, som i {@code resources/}. */
    private static String kroner(double beloep) {
        return beloep == Math.rint(beloep) ? Long.toString((long) beloep) : Double.toString(beloep);
    }
}
