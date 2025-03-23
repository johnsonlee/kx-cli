import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

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
    api(project(":fetch"))
}

val shadowJar by tasks.getting(ShadowJar::class) {
    archiveBaseName.set(project.name)
    archiveClassifier.set("all")
    archiveVersion.set("${project.version}")
    manifest {
        attributes["Main-Class"] = "io.johnsonlee.exec.cmd.HTML2CSVCommandKt"
    }
    mergeServiceFiles()
}