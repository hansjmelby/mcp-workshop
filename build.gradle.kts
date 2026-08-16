plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "no.computas"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["springAiVersion"] = "2.0.0"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.ai:spring-ai-starter-mcp-server")
	// T-17: Streamable HTTP-transport. Drar inn spring-boot-starter-web (Tomcat).
	// Begge starterne kan ligge på klassestien samtidig: alle webmvc-autokonfigurasjonene
	// er betinget av McpServerStdioDisabledCondition, så de trekker seg helt tilbake når
	// spring.ai.mcp.server.stdio=true. Tomcat holdes nede av
	// spring.main.web-application-type=none i application.properties; `http`-profilen
	// slår begge deler om.
	implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
	// SQLite-driver — versjon styres av Spring Boot sin dependency management
	runtimeOnly("org.xerial:sqlite-jdbc")
	testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
