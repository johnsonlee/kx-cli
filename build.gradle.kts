import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.Project.DEFAULT_VERSION

plugins {
    kotlin("jvm") version embeddedKotlinVersion
    kotlin("kapt") version embeddedKotlinVersion
    id("io.johnsonlee.sonatype-publish-plugin") version "1.10.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    kapt(libs.auto.service)
    kapt(libs.pico.codegen)

    implementation(kotlin("stdlib"))
    implementation(project(":base"))
    implementation(libs.auto.service)
    implementation(libs.pico.cli)
    implementation(libs.bouncycastle.bcpg.jdk18on)
    implementation(libs.bouncycastle.bcprov.jdk18on)
}

allprojects {
    group = "io.johnsonlee.${rootProject.name}"
    version = project.findProperty("version")?.takeIf { it != DEFAULT_VERSION } ?: "1.0.0-SNAPSHOT"

    repositories {
        mavenCentral()
        google()
    }

    val mainClassName = extra["main.class"] as String

    plugins.withId("com.github.johnrengelman.shadow") {
        val shadowJar by tasks.getting(ShadowJar::class) {
            archiveBaseName.set(project.name)
            archiveClassifier.set("all")
            archiveVersion.set("${project.version}")
            manifest {
                attributes["Main-Class"] = mainClassName
            }
            mergeServiceFiles()
        }

        val buildExecutable by tasks.registering {
            group = "distribution"
            description = "Build self-contained executable CLI"

            val bin = if (project != rootProject) {
                "${rootProject.name}-${project.name}"
            } else {
                project.name
            }

            println("Building executable for $bin")

            doLast {
                layout.buildDirectory.get().dir("executable").dir(bin).asFile.apply {
                    parentFile.mkdirs()
                    outputStream().use { out ->
                        rootProject.file("launcher.sh").inputStream().copyTo(out)
                        shadowJar.archiveFile.get().asFile.inputStream().copyTo(out)
                    }
                    setExecutable(true, false)
                }
            }
        }

        shadowJar.finalizedBy(buildExecutable)
    }
}
