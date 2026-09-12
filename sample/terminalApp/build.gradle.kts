@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

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
            if (buildType == org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE) {
                binaryOption("smallBinary", "true")
            }
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
            implementation(project(":lib"))
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.cio)
            implementation(libs.arrow.core)
            implementation(libs.arrow.suspendapp)
        }
    }
}
