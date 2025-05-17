plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("io.johnsonlee.sonatype-publish-plugin")
    id("com.github.johnrengelman.shadow")
}

dependencies {
    kapt(libs.auto.service)
    kapt(libs.pico.codegen)

    api(libs.jsoup)
    api(project(":dom2csv"))
}