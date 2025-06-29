plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("io.johnsonlee.sonatype-publish-plugin")
    id("com.github.johnrengelman.shadow")
}

dependencies {
    kapt(libs.auto.service)
    kapt(libs.pico.codegen)

    api(libs.auto.service)
    api(libs.johnsonlee.ktx.okhttp3)
    api(libs.okhttp)
    api(libs.okhttp.urlconnection)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(project(":base"))
}