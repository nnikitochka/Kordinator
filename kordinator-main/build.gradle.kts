import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm")
    application
    id("com.gradleup.shadow")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
    sourceSets {
        main {
            kotlin.srcDir("src/main/java")
        }
        test {
            kotlin.srcDir("src/test/java")
        }
    }
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        javaParameters.set(true)
    }
}

application {
    mainClass.set("su.nnedition.kord.Kordinator")
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.20")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.2")

    implementation("io.ktor:ktor-http-jvm:3.6.0")
    implementation("io.ktor:ktor-utils-jvm:3.6.0")
    implementation("io.ktor:ktor-server-core-jvm:3.6.0")
    implementation("io.ktor:ktor-server-netty-jvm:3.6.0")
    implementation("io.ktor:ktor-server-auth-jvm:3.6.0")
    testImplementation("io.ktor:ktor-server-test-host-jvm:3.6.0")

    implementation("com.zaxxer:HikariCP:6.3.2")
    implementation("io.dropwizard.metrics:metrics-core:4.2.40")
    implementation("io.dropwizard.metrics:metrics-jmx:4.2.35")
    implementation("org.hibernate.validator:hibernate-validator:8.0.3.Final")
    implementation("javax.cache:cache-api:1.1.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.2")
    implementation("com.github.ben-manes.caffeine:jcache:3.2.2")
    implementation("io.micrometer:micrometer-core:1.15.3")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:2.3.20")
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.12.2") {
        exclude(group = "org.hamcrest", module = "hamcrest-core")
    }
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.17.0")
    testImplementation("org.assertj:assertj-core:3.27.4")
    testImplementation("org.hamcrest:hamcrest-core:3.0")
    testRuntimeOnly("net.bytebuddy:byte-buddy-agent:1.17.7")

    implementation("com.auth0:java-jwt:4.5.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")
    implementation("org.apache.lucene:lucene-core:8.9.0")
    implementation("org.apache.lucene:lucene-analyzers-common:8.9.0")
    implementation("org.apache.commons:commons-collections4:4.5.0")
    implementation("commons-codec:commons-codec:1.18.0")
    implementation("de.androidpit:color-thief:1.1.2")
    implementation("commons-io:commons-io:2.16.1")
    implementation("commons-lang:commons-lang:2.6")
    implementation("org.apache.commons:commons-lang3:3.17.0")
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("org.tukaani:xz:1.8")
    implementation("com.github.junrar:junrar:7.4.1")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.guava:guava:30.1-jre") {
        exclude(group = "org.checkerframework", module = "checker-qual")
    }
    implementation("org.apache.httpcomponents:httpcore:4.4.16")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    runtimeOnly("org.hsqldb:hsqldb:2.5.0")
    implementation("net.jthink:jaudiotagger:3.0.1")
    implementation("org.jdom:jdom:2.0.2")
    implementation("jakarta.mail:jakarta.mail-api:2.1.3")
    implementation("com.hoodcomputing:natpmp:0.1")

    implementation("org.fourthline.cling:cling-core:2.0.1")
    implementation("org.fourthline.cling:cling-support:2.0.1")
    implementation("org.seamless:seamless-util:1.1.0")
    implementation("de.u-mass:lastfm-java:0.1.2")
    implementation("de.triology.recaptchav2-java:recaptchav2-java:1.0.4")
    implementation("org.apache.commons:commons-configuration2:2.12.0") {
        exclude(group = "commons-logging", module = "commons-logging")
    }
    runtimeOnly("com.mattbertolini:liquibase-slf4j:4.0.0")
    implementation("commons-beanutils:commons-beanutils:1.9.4") {
        exclude(group = "commons-logging", module = "commons-logging")
    }
    implementation("org.liquibase:liquibase-core:4.33.0")
    implementation("org.apache.maven:maven-artifact:3.6.0")
    implementation("com.github.muff1nman.chameleon:core:2.0.0-RELEASE") {
        exclude(group = "commons-logging", module = "commons-logging")
        exclude(group = "javax.xml.stream", module = "stax-api")
        exclude(group = "stax", module = "stax-api")
        exclude(group = "org.springframework", module = "spring-context")
        exclude(group = "commons-collections", module = "commons-collections")
    }
    implementation("com.github.muff1nman.chameleon:playlist-xspf:2.0.0-RELEASE") {
        exclude(group = "commons-logging", module = "commons-logging")
        exclude(group = "javax.xml.stream", module = "stax-api")
        exclude(group = "stax", module = "stax-api")
    }
    runtimeOnly("com.github.muff1nman.chameleon:playlist-all:2.0.0-RELEASE") {
        exclude(group = "commons-logging", module = "commons-logging")
        exclude(group = "commons-collections", module = "commons-collections")
        exclude(group = "javax.xml.stream", module = "stax-api")
        exclude(group = "stax", module = "stax-api")
    }

    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8:2.19.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.2")
    implementation("com.fasterxml.jackson.module:jackson-module-parameter-names:2.19.2")
    runtimeOnly("org.eclipse.angus:angus-mail:2.0.4")
    implementation("jakarta.annotation:jakarta.annotation-api:2.1.1")
    runtimeOnly("org.postgresql:postgresql:42.7.7") {
        exclude(group = "org.checkerframework", module = "checker-qual")
    }
    runtimeOnly("com.mysql:mysql-connector-j:9.4.0")
    runtimeOnly("org.mariadb.jdbc:mariadb-java-client:3.5.5")
}

sourceSets {
    main {
        java.setSrcDirs(listOf("src/main/java"))
    }
    test {
        java.setSrcDirs(listOf("src/test/java"))
    }
}

val generatedBuildMetadata = layout.buildDirectory.dir("generated/build-metadata")
sourceSets.main {
    resources.srcDir(generatedBuildMetadata)
}

val generateBuildMetadata = tasks.register("generateBuildMetadata") {
    val metadataFile = generatedBuildMetadata.map { it.file("build.properties") }
    outputs.file(metadataFile)
    outputs.upToDateWhen { false }

    doLast {
        val revision = try {
            ProcessBuilder("git", "rev-parse", "--short=12", "HEAD")
                .directory(rootProject.projectDir)
                .start()
                .inputStream
                .bufferedReader()
                .readText()
                .trim()
        } catch (_: Exception) {
            "UNKNOWN"
        }.ifBlank { "UNKNOWN" }
        val timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now())
        val file = metadataFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText("version=${project.version}\ntimestamp=$timestamp\nrevision=$revision\n")
    }
}

tasks.processResources {
    dependsOn(generateBuildMetadata)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        javaParameters.set(true)
    }
}

tasks.test {
    useJUnitPlatform()
    forkEvery = 1
    filter.isFailOnNoMatchingTests = false
    exclude("**/service/search/*TestCase.class")
    doFirst {
        val agent = configurations.testRuntimeClasspath.get().files
            .firstOrNull { it.name.startsWith("byte-buddy-agent-") }
        if (agent != null) jvmArgs("-javaagent:${agent.absolutePath}")
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set("kordinator.jar")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    mergeServiceFiles()
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    filesMatching("META-INF/*.kotlin_module") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA")
    manifest {
        attributes(
            "Main-Class" to application.mainClass.get(),
            "Multi-Release" to "true",
        )
    }
}

tasks.assemble {
    dependsOn(tasks.named("shadowJar"))
}

val checksumArtifacts = tasks.register("checksumArtifacts") {
    group = "distribution"
    description = "Writes a SHA-256 checksum for the executable jar."
    dependsOn(tasks.named("shadowJar"))
    val jarFile = tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile }
    val checksumFile = layout.buildDirectory.file("artifacts-checksums.sha")
    outputs.file(checksumFile)

    doLast {
        val artifact = jarFile.get().asFile
        val digest = MessageDigest.getInstance("SHA-256").digest(artifact.readBytes())
            .joinToString("") { "%02x".format(it) }
        checksumFile.get().asFile.writeText("$digest  ${artifact.name}\n")
    }
}

tasks.register("releaseArtifacts") {
    group = "distribution"
    description = "Builds the executable jar and its SHA-256 checksum."
    dependsOn(checksumArtifacts)
}
