package no.computas.vacationmcp.config;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Logger navnene på registrerte {@link McpTool}-verktøy ved oppstart — en hjelp under
 * workshopen, slik at du raskt ser at et nytt verktøy faktisk ble plukket opp.
 *
 * <p>Slås av/på med {@code workshop.log-registered-tools} i {@code application.properties}.
 * Loggen går til konsollet (stderr) via {@code logback-spring.xml}.
 */
@Component
@ConditionalOnProperty(name = "workshop.log-registered-tools", havingValue = "true", matchIfMissing = true)
public class RegisteredToolsLogger {

    private static final Logger log = LoggerFactory.getLogger(RegisteredToolsLogger.class);

    private final ApplicationContext applicationContext;

    public RegisteredToolsLogger(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logRegisteredTools() {
        List<String> toolNames = new ArrayList<>();
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                continue; // hopp over beans som ikke kan hentes ut her
            }
            Class<?> targetClass = ClassUtils.getUserClass(bean);
            ReflectionUtils.doWithMethods(targetClass, method -> {
                McpTool tool = AnnotatedElementUtils.findMergedAnnotation(method, McpTool.class);
                if (tool != null) {
                    toolNames.add(StringUtils.hasText(tool.name()) ? tool.name() : method.getName());
                }
            });
        }

        if (toolNames.isEmpty()) {
            log.info("Ingen MCP-tools registrert ennå — implementer en @McpTool (se BACKLOG.md).");
        } else {
            log.info("Tilgjengelige MCP-tools ({}): {}", toolNames.size(), toolNames);
        }
    }
}
