plugins {
    `java-library`
}

dependencies {
    // LEGA API
    api(project(":lega-api"))
    api(project(":lega-protocol"))
    api(project(":lega-bungeecord-bridge"))
    api(project(":lega-velocity-bridge"))
    api(project(":lega-async-engine"))
    api(project(":lega-performance-engine"))
    api(project(":lega-security"))
    api(project(":lega-profiler"))
    api(project(":lega-world-engine"))

    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // Netty — runtime implementation (used by LegaNettyServer)
    implementation("io.netty:netty-all:4.1.111.Final")

    // SLF4J + Log4j2
    implementation("org.apache.logging.log4j:log4j-api:2.23.1")
    implementation("org.apache.logging.log4j:log4j-core:2.23.1")
    implementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.23.1")

    // Guava / Caffeine
    implementation("com.google.guava:guava:33.1.0-jre")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // FastUtil
    implementation("it.unimi.dsi:fastutil:8.5.13")

    // TerminalConsoleAppender for fancy console
    implementation("net.minecrell:terminalconsoleappender:1.3.0")
    implementation("org.jline:jline-terminal-jansi:3.25.1")
    implementation("org.jline:jline-reader:3.25.1")

    // Gson / SnakeYAML
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.yaml:snakeyaml:2.2")
}

tasks.jar {
    dependsOn(configurations.runtimeClasspath)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("LEGA-${project.version}.jar")
    manifest {
        attributes(
            "Main-Class" to "net.lega.server.bootstrap.LegaBootstrap",
            "Multi-Release" to "true"
        )
    }
    from(configurations.runtimeClasspath.get()
        .map { if (it.isDirectory) it else zipTree(it) }
    )
}

tasks.build {
    dependsOn(tasks.jar)
}
