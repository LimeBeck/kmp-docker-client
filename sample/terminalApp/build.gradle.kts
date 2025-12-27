plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    listOf(
        linuxX64(),
    ).forEach {
        it.binaries.executable {
            entryPoint = "main"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
        }
    }
}
