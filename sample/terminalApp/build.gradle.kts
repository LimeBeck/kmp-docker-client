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
            binaryOption("smallBinary", "true")
        }
    }

    js {
        nodejs {
            binaries.executable()
        }
    }

    jvm {
        mainRun {
            mainClass = "MainKt"
        }
    }


    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.arrow.core)
            implementation(libs.arrow.suspendapp)
        }
    }
}
