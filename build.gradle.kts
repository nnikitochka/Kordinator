import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec

plugins {
    kotlin("jvm") version "2.3.20" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
}

group = "su.nnedition.kord"
version = providers.gradleProperty("kordinatorVersion").getOrElse("0.1.0")

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        maven {
            name = "projectArtifacts"
            url = rootProject.layout.projectDirectory.dir("repo").asFile.toURI()
        }
        mavenLocal {
            content {
                includeGroup("org.fourthline.cling")
                includeGroup("org.seamless")
            }
        }
        maven {
            name = "clingRepository"
            url = uri("http://4thline.org/m2")
            isAllowInsecureProtocol = true
        }
    }
}

tasks.register<Copy>("prepareDockerContext") {
    group = "distribution"
    description = "Copies the executable Kordinator jar into the Docker build context."
    dependsOn(":kordinator-main:shadowJar")
    from(project(":kordinator-main").layout.buildDirectory.file("libs/kordinator.jar"))
    into(layout.projectDirectory.dir("install/docker/build/dependency"))
}

tasks.register<Exec>("dockerBuild") {
    group = "distribution"
    description = "Builds the local Kordinator Docker image."
    dependsOn("prepareDockerContext")
    workingDir(layout.projectDirectory.dir("install/docker"))
    commandLine("docker", "build", "-t", "ghcr.io/nnikitochka/kordinator:latest", ".")
}
