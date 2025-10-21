plugins {
    kotlin("jvm") version "2.1.10"
    kotlin("plugin.serialization") version "2.1.10"
}

group = "eu.ehri_project.ehri_kg"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("com.herminiogarcia:shexml-streaming_3:0.0.1")
    implementation("io.ktor:ktor-client-core:3.3.1")
    implementation("io.ktor:ktor-client-cio:3.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("io.github.oshai:kotlin-logging-jvm:5.1.0")
    implementation("ch.qos.logback:logback-classic:1.3.5")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}