import java.util.Properties
import java.util.StringTokenizer

plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("io.johnsonlee.sonatype-publish-plugin")
}

repositories {
    mavenCentral()
}

dependencies {
    kapt(libs.auto.service)
    kapt(libs.pico.codegen)

    api(kotlin("stdlib"))
    api(libs.kotlinx.coroutines.core)
    api(libs.auto.service)
    api(libs.pico.cli)

    testImplementation(kotlin("test"))
    testImplementation(kotlin("test-junit"))
}

val generateVersionProperties by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources")
    val outputFile = outputDir.map { it.file("version.properties") }
    val head = arrayOf(".git", "logs", "HEAD").joinToString(File.separator)
    val revision = File(project.rootProject.projectDir, head).takeIf {
        it.exists() && it.canRead() && it.length() > 0
    }?.useLines {
        StringTokenizer(it.last()).asSequence().take(2).last()
    } ?: ""

    outputs.file(outputFile)

    doLast {
        val properties = Properties().apply {
            this["version"] = project.version.toString()
            this["revision"] = revision
        }

        outputFile.get().asFile.also {
            it.parentFile.mkdirs()
        }.outputStream().use {
            properties.store(it, null)
        }
    }
}

tasks.withType(ProcessResources::class.java).configureEach {
    dependsOn(generateVersionProperties)

    from(generateVersionProperties.map { it.outputs.files }) {
        into("")
    }
}
