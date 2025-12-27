import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlinx.serialization)
    id("org.openapi.generator") version "7.18.0"
}

val schemaFilePath = "$rootDir/specs/v1.51.yaml"

kotlin {
    linuxX64()

    sourceSets {
        commonMain {
            kotlin.srcDir("$buildDir/generated/openapi/src/main/kotlin")
            dependencies {
                implementation(kotlin("stdlib")) // Обязательно
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
            "models" to "", // Генерировать модели

            // ОТКЛЮЧАЕМ МУСОР:
            "apis" to "false",   // Не Генерировать API
            "supportingFiles" to "false", // Не генерировать вспомогательные файлы (ApiClient и т.д.)
            "modelDocs" to "false", // Не генерировать доки для моделей (markdown)
            "apiDocs" to "false",   // Не генерировать доки для API (markdown)
            "modelTests" to "false", // Не генерировать тесты моделей
            "apiTests" to "false"    // Не генерировать тесты API
        )
    )

    configOptions.set(
        mapOf(
            // ЭТОТ ПАРАМЕТР ВКЛЮЧАЕТ KTOR
            "library" to "multiplatform",
            "dateLibrary" to "kotlinx-datetime",
            "useCoroutines" to "true",

            // Опционально: не генерировать инфраструктурные классы, если хотите свой setup
            "omitGradlePluginVersions" to "true",
            "omitGradleWrapper" to "true",
            "enumPropertyNaming" to "UPPERCASE"
        )
    )
}
