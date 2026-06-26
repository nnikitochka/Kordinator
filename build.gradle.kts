plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

group = "su.nnedition.kordinator"
version = "0.1"

application {
    mainClass = "io.ktor.server.cio.EngineMain"
}

kotlin {
    jvmToolchain(21)
}
dependencies {
    implementation(ktorLibs.server.auth)
    implementation(ktorLibs.server.cio)
    implementation(ktorLibs.server.core)
    implementation(libs.logback.classic)
    implementation(libs.kotlinx.coroutines.core)
    implementation("io.ktor:ktor-server-content-negotiation:3.5.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.5.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")

    // Парсинг акцентных цветов
    implementation("de.androidpit:color-thief:1.1.2")

    //
    implementation("org.mindrot:jbcrypt:0.4")

    // БД
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.sqlite)

    // Теги
    implementation("net.jthink:jaudiotagger:3.0.1")

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
