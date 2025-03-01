import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project.DEFAULT_VERSION

plugins {
    kotlin("jvm") version embeddedKotlinVersion
    kotlin("kapt") version embeddedKotlinVersion
    id("io.johnsonlee.sonatype-publish-plugin") version "1.9.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "io.johnsonlee"
version = project.findProperty("version")?.takeIf { it != DEFAULT_VERSION } ?: "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
    google()
}

dependencies {
    kapt(libs.auto.service)
    kapt(libs.pico.codegen)

    implementation(kotlin("bom"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation(libs.auto.service)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.module.jsr310)
    implementation(libs.okhttp)
    implementation(libs.okhttp.urlconnection)
    implementation(libs.playwright)
    implementation(libs.pico.cli)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

val shadowJar by tasks.getting(ShadowJar::class) {
    archiveBaseName.set("exec")
    archiveClassifier.set("all")
    archiveVersion.set("${project.version}")
    manifest {
        attributes["Main-Class"] = "io.johnsonlee.exec.MainKt"
    }
}
