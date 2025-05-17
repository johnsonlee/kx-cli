plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("io.johnsonlee.sonatype-publish-plugin")
    id("com.github.johnrengelman.shadow")
}

dependencies {
    kapt(libs.auto.service)
    kapt(libs.pico.codegen)

    api(libs.okhttp)
    api(libs.okhttp.urlconnection)
    api(libs.kotlinx.serialization.json)
    api(project(":base"))
}