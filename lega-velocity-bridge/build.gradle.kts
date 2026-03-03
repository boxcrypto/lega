plugins {
    `java-library`
}

dependencies {
    api(project(":lega-api"))
    implementation("com.google.guava:guava:33.1.0-jre")
    implementation("com.google.code.gson:gson:2.10.1")
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-all:4.1.111.Final")
}
