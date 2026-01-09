plugins {
    alias(libs.plugins.multiplatform)
}

kotlin {
    linuxX64().binaries.executable {
        entryPoint = "main"
        binaryOption("smallBinary", "true")
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":lib"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.server.core)
            implementation(libs.ktor.server.cio)
            implementation(libs.ktor.server.html.builder)
            implementation(libs.kotlinx.html)
            implementation(libs.limebeck.common)
        }
    }
}
