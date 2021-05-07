plugins {
    kotlin("jvm") version "1.5.0"
    kotlin("plugin.serialization") version "1.5.0"
    id("com.github.ben-manes.versions") version "0.38.0"
}

group = "kweb"
version = "0.4.13"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.google.guava:guava:30.1.1-jre")
    implementation("net.incongru.watchservice:barbary-watchservice:1.0")
    implementation("org.lmdbjava:lmdbjava:0.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.2.0")
    api("org.mapdb:mapdb:3.0.8")

    testImplementation("io.kotest:kotest-runner-junit5-jvm:4.5.0")
    testImplementation("io.kotest:kotest-assertions-core-jvm:4.5.0")
    testImplementation("io.kotest:kotest-property:4.5.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}