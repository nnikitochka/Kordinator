plugins {
    kotlin("jvm")
}

group = "su.nnedition.kord"
version = "0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}