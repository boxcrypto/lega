import org.gradle.api.tasks.compile.JavaCompile

plugins {
    java
    `java-library`
    id("com.github.johnrengelman.shadow") version "8.1.1" apply false
}

val legaVersion = "1.0.0-SNAPSHOT"
val minJavaVersion = JavaVersion.VERSION_21

allprojects {
    group = "net.lega"
    version = legaVersion

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

subprojects {
    apply(plugin = "java-library")

    java {
        sourceCompatibility = minJavaVersion
        targetCompatibility = minJavaVersion
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf(
            "--enable-preview",
            "-Xlint:all",
            "-Xlint:-processing"
        ))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        jvmArgs("--enable-preview")
    }

    tasks.withType<Jar> {
        manifest {
            attributes[
                "Implementation-Title"]   = project.name
            attributes["Implementation-Version"] = project.version
            attributes["Implementation-Vendor"]  = "maatsuh"
            attributes["Built-By"]               = "maatsuh"
        }
    }

    dependencies {
        compileOnly("org.jetbrains:annotations:24.1.0")
        testImplementation(platform("org.junit:junit-bom:5.10.2"))
        testImplementation("org.junit.jupiter:junit-jupiter")
    }
}
