plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("io.johnsonlee.sonatype-publish-plugin")
}

repositories {
    mavenCentral()
}

dependencies {
    api(kotlin("stdlib"))
    api(libs.pico.cli)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}
