/**
 * lega-protocol — Multi-version Minecraft protocol adapter layer.
 *
 * Handles protocol translation from 1.7.10 to 1.21.x.
 *
 * @author maatsuh
 */

plugins {
    `java-library`
}

dependencies {
    api(project(":lega-api"))

    // Netty for packet I/O
    api("io.netty:netty-all:4.1.111.Final")

    // Guava for utilities
    api("com.google.guava:guava:33.1.0-jre")

    // FastUtil for primitive collections (PacketRegistry maps)
    api("it.unimi.dsi:fastutil:8.5.13")

    // Gson for packet data structures
    api("com.google.code.gson:gson:2.11.0")

    // SLF4J
    compileOnly("org.slf4j:slf4j-api:2.0.13")
}

tasks.jar {
    manifest {
        attributes["Implementation-Title"]   = "LEGA Protocol"
        attributes["Implementation-Version"] = project.version
        attributes["Implementation-Vendor"]  = "maatsuh"
    }
}
