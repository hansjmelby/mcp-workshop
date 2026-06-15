package no.computas.vacationmcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Component;

/**
 * Det aller første og enkleste verktøyet i workshop-skallet.
 *
 * <p>Metoder annotert med {@link McpTool} på en Spring-bean blir automatisk
 * oppdaget og eksponert som MCP-verktøy av Spring AI sin annotasjons-scanner.
 * Senere oppgaver i {@code BACKLOG.md} legger til verktøy som faktisk snakker
 * med databasen (destinasjoner, tilgjengelighet, bookinger).
 */
@Component
public class AboutTool {

    @McpTool(
            name = "about_application",
            description = "Forklarer hva denne applikasjonen er og hva den brukes til.")
    public String aboutApplication() {
        return """
                Dette er en ferie-booking MCP-server bygget i Spring Boot med Spring AI.
                Den er skallet for en workshop der du lærer å lage MCP-servere ved å \
                jobbe deg gjennom en backlog (se BACKLOG.md): du implementerer verktøy \
                (tools), ressurser (resources) og prompts som lar en LLM søke etter \
                feriedestinasjoner, sjekke tilgjengelighet og priser, og opprette bookinger \
                mot en SQLite-database.""";
    }
}
