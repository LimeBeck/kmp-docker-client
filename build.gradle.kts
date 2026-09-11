plugins {
    alias(libs.plugins.multiplatform).apply(false)
    alias(libs.plugins.maven.publish).apply(false)
    alias(libs.plugins.kotlinx.serialization).apply(false)
    alias(libs.plugins.versions)
}

val libVersion = providers.gradleProperty("libVersion").get()
group = "dev.limebeck.libs"
version = libVersion

subprojects {
    group = rootProject.group
    version = rootProject.version
}
