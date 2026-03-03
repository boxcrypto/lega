rootProject.name = "LEGA"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://repo.codemc.io/repository/maven-releases/")
        maven("https://repo.codemc.io/repository/maven-snapshots/")
        maven("https://jitpack.io")
        maven("https://repo.velocitypowered.com/snapshots/")
    }
}

include(
    ":lega-api",
    ":lega-protocol",
    ":lega-server",
    ":lega-async-engine",
    ":lega-performance-engine",
    ":lega-security",
    ":lega-profiler",
    ":lega-world-engine",
    ":lega-velocity-bridge",
    ":lega-bungeecord-bridge"
)
