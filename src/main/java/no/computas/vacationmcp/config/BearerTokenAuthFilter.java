package no.computas.vacationmcp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bearer-token-auth for HTTP-transporten (T-18).
 *
 * <p><b>Gjelder bare profilen {@code http}.</b> I stdio-modus finnes det ingen nettverksflate
 * å beskytte — serveren er en prosess hosten selv startet, og arver dens rettigheter — så
 * {@code @Profile("http")} gjør at denne beanen ikke engang opprettes da.
 *
 * <p>Filteret kjører foran <i>alle</i> forespørsler, ikke bare {@code /mcp}. Applikasjonen har
 * nøyaktig ett endepunkt, og «nekt alt som ikke er eksplisitt åpnet» er den regelen som holder
 * seg når noen senere legger til et endepunkt til. Det dekker samtidig alle tre metodene
 * Streamable HTTP bruker mot samme sti: {@code POST} (JSON-RPC), {@code GET} (SSE-strømmen) og
 * {@code DELETE} (avslutt sesjon).
 *
 * <p>Sjekken skjer <i>før</i> JSON-RPC. Et avvist kall får derfor et HTTP-svar
 * ({@code 401} + {@code WWW-Authenticate}), ikke en JSON-RPC-{@code error} — autentisering er
 * et transportanliggende, og en klient som ikke slipper inn har ingen sesjon å feile innenfor.
 *
 * <p>Konfigurasjon (se {@code application-http.properties}):
 * <ul>
 *   <li>{@code workshop.http.auth.token} — tokenet. Miljøvariabel:
 *       {@code WORKSHOP_HTTP_AUTH_TOKEN}. Står den tom, genereres et tilfeldig token for
 *       kjøringen og skrives i oppstartsloggen.</li>
 *   <li>{@code workshop.http.auth.enabled} — {@code false} skrur av auth helt (med et tydelig
 *       WARN). Kun for demo/feilsøking.</li>
 * </ul>
 *
 * <p><b>Dette er ikke OAuth.</b> Et statisk delt token sier bare «du kjenner hemmeligheten» —
 * det er ingen bruker, ingen scopes, ingen utløpstid og ingen mulighet til å trekke tilbake
 * tilgang for én klient. MCP-spesifikasjonen har en egen autorisasjonsmodell for remote-servere
 * (OAuth 2.1 med Protected Resource Metadata), og der er dette 401-svaret bare første steg:
 * en ekte MCP-server peker klienten videre til sin authorization server i
 * {@code WWW-Authenticate}. Se <a href="https://modelcontextprotocol.io/specification/draft/basic/authorization">
 * MCP · Authorization</a>.
 */
@Component
@Profile("http")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class BearerTokenAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(BearerTokenAuthFilter.class);

    /** RFC 6750: skjemaet er «Bearer», og det skilles fra tokenet med ett mellomrom. */
    private static final String BEARER_PREFIX = "Bearer ";

    private static final String REALM = "vacation-booking-mcp";

    private final boolean enabled;
    private final String token;
    private final boolean generated;

    public BearerTokenAuthFilter(
            @Value("${workshop.http.auth.enabled:true}") boolean enabled,
            @Value("${workshop.http.auth.token:}") String configuredToken) {
        this.enabled = enabled;
        // Ingen token konfigurert? Da lager vi ett i stedet for å kjøre åpent. Se logAuthState().
        this.generated = enabled && !StringUtils.hasText(configuredToken);
        this.token = this.generated ? UUID.randomUUID().toString() : configuredToken.trim();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header) || !header.startsWith(BEARER_PREFIX)) {
            // RFC 6750: mangler det legitimasjon, er utfordringen bare «Bearer realm=…» —
            // ingen error-kode, for klienten har ennå ikke gjort noe galt.
            reject(request, response, null, "mangler Authorization: Bearer <token>");
            return;
        }

        String presented = header.substring(BEARER_PREFIX.length()).trim();
        if (!matches(presented)) {
            reject(request, response, "invalid_token", "ugyldig token");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Sammenligner i konstant tid. Med {@code String.equals} kan kjøretiden i prinsippet lekke
     * hvor mange tegn av tokenet en angriper har gjettet riktig; {@code MessageDigest.isEqual}
     * bryter ikke ut tidlig. Én linje, så vi tar den — men merk at den ikke gjør et statisk
     * delt token til noe annet enn et statisk delt token.
     */
    private boolean matches(String presented) {
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private void reject(HttpServletRequest request, HttpServletResponse response,
                        String error, String reason) throws IOException {
        log.warn("401 Unauthorized: {} {} fra {} — {}",
                request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), reason);

        // WWW-Authenticate er det som gjør 401 til noe annet enn 403: det forteller klienten
        // HVORDAN den skal autentisere seg. 403 ville betydd «du er kjent, men har ikke lov».
        String challenge = "Bearer realm=\"" + REALM + "\"";
        if (error != null) {
            challenge += ", error=\"" + error + "\", error_description=\"" + reason + "\"";
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, challenge);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"error":"unauthorized","message":"Send 'Authorization: Bearer <token>'. \
                Tokenet settes med workshop.http.auth.token / WORKSHOP_HTTP_AUTH_TOKEN."}""");
    }

    /** Gjør tilstanden synlig ved oppstart — en server som stille kjører uåpen er en dårlig lærdom. */
    @EventListener(ApplicationReadyEvent.class)
    public void logAuthState() {
        if (!enabled) {
            log.warn("""
                    ADVARSEL: MCP-endepunktet er UBESKYTTET (workshop.http.auth.enabled=false).
                    Alle som når porten kan kalle create_booking og cancel_booking.""");
        } else if (generated) {
            log.warn("""
                    Ingen workshop.http.auth.token er satt — genererte et tilfeldig token for denne kjøringen:

                        Authorization: Bearer {}

                    Tokenet endres ved hver omstart. Sett WORKSHOP_HTTP_AUTH_TOKEN (eller
                    workshop.http.auth.token) for et fast token.""", token);
        } else {
            log.info("MCP-endepunktet krever bearer-token (workshop.http.auth.token, {} tegn).",
                    token.length());
        }
    }
}
