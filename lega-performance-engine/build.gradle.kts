plugins {
    `java-library`
}

dependencies {
    api(project(":lega-api"))
    api(project(":lega-async-engine"))
    implementation("com.google.guava:guava:33.1.0-jre")
    implementation("it.unimi.dsi:fastutil:8.5.13")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-all:4.1.107.Final")
}
