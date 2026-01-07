import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.openapi.generator)
}

val schemaFilePath = "$rootDir/specs/v1.51.yaml"

kotlin {
    linuxX64()

    js {
        nodejs()
    }

    jvm {}

    sourceSets {
        commonMain {
            kotlin.srcDir("$buildDir/generated/openapi/src/main/kotlin")
            dependencies {
                implementation(kotlin("stdlib")) // Required
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.datetime)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.client.serialization)
                implementation(libs.ktor.serialization.json)
            }

        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        linuxMain.dependencies {
            implementation(libs.ktor.client.curl)
        }

    }

    //https://kotlinlang.org/docs/native-objc-interop.html#export-of-kdoc-comments-to-generated-objective-c-headers
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget> {
        compilations["main"].compileTaskProvider.configure {
            compilerOptions {
                freeCompilerArgs.add("-Xexport-kdoc")
            }
        }
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    dependsOn("openApiGenerate")
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(schemaFilePath)
    outputDir.set("$buildDir/generated/openapi")
    apiPackage.set("dev.limebeck.docker.api")
    modelPackage.set("dev.limebeck.docker.client.model")
    ignoreFileOverride.set("$rootDir/.openapi-generator-ignore")

    globalProperties.set(
        mapOf(
            "models" to "", // Generate models

            // DISABLE JUNK:
            "apis" to "false",   // Do not generate API
            "supportingFiles" to "false", // Do not generate supporting files (ApiClient etc.)
            "modelDocs" to "false", // Do not generate model docs (markdown)
            "apiDocs" to "false",   // Do not generate API docs (markdown)
            "modelTests" to "false", // Do not generate model tests
            "apiTests" to "false"    // Do not generate API tests
        )
    )

    configOptions.set(
        mapOf(
            // THIS PARAMETER ENABLES KTOR
            "library" to "multiplatform",
            "dateLibrary" to "kotlinx-datetime",
            "useCoroutines" to "true",

            // Optional: do not generate infrastructure classes if you want your own setup
            "omitGradlePluginVersions" to "true",
            "omitGradleWrapper" to "true",
            "enumPropertyNaming" to "UPPERCASE"
        )
    )
}
