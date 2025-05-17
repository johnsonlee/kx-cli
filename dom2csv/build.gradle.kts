plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("io.johnsonlee.sonatype-publish-plugin")
}

dependencies {
    kapt(libs.auto.service)
    kapt(libs.pico.codegen)

    api(project(":fetch"))
}