plugins {
    kotlin("jvm")
}

group = "su.nnedition.kord.library"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}