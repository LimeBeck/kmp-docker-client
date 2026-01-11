import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.openapi.generator)
    alias(libs.plugins.dokka)
}

val schemaFilePath = "$rootDir/specs/v1.51.yaml"

kotlin {
    linuxX64()

    js {
        nodejs {
            testTask {
                useMocha {
                    timeout = "30s"
                }
            }
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

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
                implementation(libs.limebeck.common)
            }

        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        jvmTest.dependencies {
            implementation(libs.logback)
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

tasks.named("jsSourcesJar") {
    dependsOn("openApiGenerate")
}

tasks.named("dokkaGeneratePublicationHtml") {
    dependsOn("openApiGenerate")
}

tasks.named("jvmSourcesJar") {
    dependsOn("openApiGenerate")
}

tasks.named("sourcesJar") {
    dependsOn("openApiGenerate")
}

tasks.named("linuxX64SourcesJar") {
    dependsOn("openApiGenerate")
}

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set(schemaFilePath)
    outputDir.set("$buildDir/generated/openapi")
    apiPackage.set("dev.limebeck.libs.docker.api")
    modelPackage.set("dev.limebeck.libs.docker.client.model")
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

    typeMappings.set(mapOf(
        "object" to "JsonObject",
        "AnyType" to "JsonElement"
    ))

    importMappings.set(mapOf(
        "JsonObject" to "kotlinx.serialization.json.JsonObject",
        "JsonElement" to "kotlinx.serialization.json.JsonElement"
    ))

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

// Fix for @SerialName(value = "Ports") val ports: kotlin.collections.Map<kotlin.String, kotlin.collections.List<PortBinding>>? = null
tasks.named("openApiGenerate") {
    doLast {
        val genDir = file("$buildDir/generated/openapi")

        fileTree(genDir).matching { include("**/*.kt") }.forEach { file ->
            val content = file.readText()

            if (content.contains("List<PortBinding>>")) {
                val fixedContent = content.replace(
                    "List<PortBinding>>",
                    "List<PortBinding>?>"
                )
                file.writeText(fixedContent)
                println("🩹 [PATCH] Fix Nullability in PortMap for file: ${file.name}")
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    coordinates("dev.limebeck.libs", "docker-client", project.version.toString())

    pom {
        url.set("https://github.com/LimeBeck/kmp-docker-client")
        name.set("docker-client")
        description.set("Kotlin Multiplatform Client for Docker")
        developers {
            developer {
                id.set("LimeBeck")
                name.set("Anatoly Nechay-Gumen")
                email.set("mail@limebeck.dev")
            }
        }
        licenses {
            license {
                name.set("MIT license")
                url.set("https://github.com/LimeBeck/kmp-docker-client/blob/master/LICENCE")
                distribution.set("repo")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/LimeBeck/kmp-docker-client.git")
            developerConnection.set("scm:git:ssh://github.com/LimeBeck/kmp-docker-client.git")
            url.set("https://github.com/LimeBeck/kmp-docker-client")
        }
    }
}

dokka {
    moduleName.set("Kotlin Multiplatform Client for Docker")

    dokkaPublications.html {
        suppressInheritedMembers.set(true)
        failOnWarning.set(true)
    }

//    dokkaSourceSets.configureEach {
//        includes.from("../README.MD")
//    }

    pluginsConfiguration.html {
        footerMessage.set("(c) LimeBeck.Dev")
    }
}
