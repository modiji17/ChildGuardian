pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // This line tells Gradle to look for libraries ONLY in this file, not in individual apps
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {

        mavenCentral()
        google()
        // Adding this because WebRTC or Socket.io sometimes need it
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "ChildGuardian"
include(":app")