plugins {
    `java-library`
}

description = "LEGA BungeeCord/Waterfall/Travertine/Velocity Bridge"

dependencies {
    // Core LEGA API
    api(project(":lega-api"))

    // Netty for low-level channel handler integration
    implementation("io.netty:netty-all:4.1.111.Final")

    // Guava (for HMAC utilities in BungeeGuard verification)
    implementation("com.google.guava:guava:33.1.0-jre")

    // Gson (for skin property JSON parsing)
    implementation("com.google.code.gson:gson:2.11.0")

    // SLF4J logging
    implementation("org.slf4j:slf4j-api:2.0.12")

    // YAML config (SnakeYAML)
    implementation("org.yaml:snakeyaml:2.2")
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveClassifier.set("all")
    manifest {
        attributes["Implementation-Title"]   = "lega-bungeecord-bridge"
        attributes["Implementation-Version"] = project.version
        attributes["Implementation-Vendor"]  = "maatsuh"
        attributes["Built-By"]               = "maatsuh"
    }
    from(configurations.runtimeClasspath.get()
        .filter { it.isFile }
        .map { zipTree(it) }
    )
}
