plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("io.johnsonlee.sonatype-publish-plugin")
    id("com.github.johnrengelman.shadow")
}

dependencies {
    kapt(libs.auto.service)
    kapt(libs.pico.codegen)

    api(project(":fetch"))
    api(libs.jackson.dataformat.xml)
    api(libs.jsonpath)
    api(libs.jsoup)
    api(libs.tika.core)
}