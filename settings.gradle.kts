rootProject.name = "kmp-docker-client"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(":lib")
include(":sample:terminalApp")
include(":sample:htmxDashboard")
