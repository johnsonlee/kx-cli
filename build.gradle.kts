import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.util.Properties
import java.util.StringTokenizer
import org.gradle.api.Project.DEFAULT_VERSION

plugins {
    kotlin("jvm") version embeddedKotlinVersion
    kotlin("kapt") version embeddedKotlinVersion
    id("project-report")
    id("io.johnsonlee.sonatype-publish-plugin") version "1.10.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    kapt(libs.pico.codegen)

    implementation(kotlin("stdlib"))
    implementation(project(":base"))
    implementation(libs.pico.cli)
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

    val dependencySizeReport by tasks.registering {
        group = "reporting"
        description = "Prints the size of all runtimeClasspath dependency JARs"

        doLast {
            val config = configurations.runtimeClasspath.get()
            println("Dependency Size Report:")
            println("=".repeat(50))

            val entries = config
                .filter { it.name.endsWith(".jar") }
                .map { file -> file to file.length() }
                .sortedByDescending { it.second }

            var total = 0L
            for ((file, size) in entries) {
                println(String.format("%-60s %8.2f MB", file.name, size / 1024.0 / 1024.0))
                total += size
            }

            println("-".repeat(50))
            println(String.format("%-60s %8.2f MB", "TOTAL", total / 1024.0 / 1024.0))
        }
    }
}

val shadowJar by tasks.getting(ShadowJar::class)
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
