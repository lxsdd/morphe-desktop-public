pluginManagement {
    repositories {
        // Resolve Maven Central plugin transitive artifacts through the same
        // mirror used by CI; Gradle Plugin Portal remains available for markers.
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "morphe-desktop"

// Include morphe-patcher and morphe-library as composite builds if they exist locally
mapOf(
    "morphe-patcher" to "app.morphe:morphe-patcher",
    "morphe-library" to "app.morphe:morphe-library",
).forEach { (libraryPath, libraryName) ->
    val libDir = file("../$libraryPath")
    if (libDir.exists()) {
        includeBuild(libDir) {
            dependencySubstitution {
                substitute(module(libraryName)).using(project(":"))
            }
        }
    }
}
