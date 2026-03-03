plugins {
    `java-library`
}

dependencies {
    // Paper API - multi-version support
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    
    // Netty for packet system
    compileOnly("io.netty:netty-all:4.1.107.Final")
    
    // Guava
    implementation("com.google.guava:guava:33.1.0-jre")
    
    // FastUtil for high-performance collections
    implementation("it.unimi.dsi:fastutil:8.5.13")
    
    // Caffeine cache
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    
    // YAML config
    implementation("org.yaml:snakeyaml:2.2")
    
    // JSON
    implementation("com.google.code.gson:gson:2.10.1")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")
    
    // Annotations
    compileOnly("org.jetbrains:annotations:24.1.0")
}

java {
    withSourcesJar()
}
