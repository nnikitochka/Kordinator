pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kordinator"

include(":kordinator-main")

include("kordinator-api")