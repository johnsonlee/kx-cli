plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("io.johnsonlee.sonatype-publish-plugin")
    id("com.github.johnrengelman.shadow")
}

dependencies {
    kapt(libs.auto.service)
    kapt(libs.pico.codegen)

    api(libs.playwright)
    api(project(":base"))
}