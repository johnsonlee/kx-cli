import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Project.DEFAULT_VERSION

plugins {
    kotlin("jvm") version embeddedKotlinVersion
    kotlin("kapt") version embeddedKotlinVersion
    id("io.johnsonlee.sonatype-publish-plugin") version "1.10.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":browser"))
    implementation(project(":fetch"))
    implementation(project(":html2csv"))
    implementation(project(":json2csv"))
    implementation(project(":xml2json"))
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
            configureBuildExecutable(shadowJar.archiveFile)
        }

        shadowJar.finalizedBy(buildExecutable)
    }
}

val shadowJar by tasks.getting(ShadowJar::class)

setOf("linux", "linux-arm64", "mac", "mac-arm64", "win32_x64").let { platforms ->
    platforms.forEach { platform ->
        val suffix = platform.toCamelCase()
        val taskName = "shadowJarFor${suffix}"
        val shadowJarForPlatform = tasks.register<ShadowJar>(taskName) {
            // copy from shadowJar
            archiveBaseName.set(shadowJar.archiveBaseName)
            archiveVersion.set(shadowJar.archiveVersion)
            manifest = shadowJar.manifest
            configurations = shadowJar.configurations
            from(shadowJar.source)

            // set platform classifier
            archiveClassifier.set(platform)

            // remove unnecessary files
            transform(object : Transformer {
                override fun getName() = "exclude-unused-playwright-driver"
                override fun canTransformResource(element: FileTreeElement): Boolean {
                    val path = element.relativePath.pathString
                    if (path.startsWith("driver/")) {
                        return !path.startsWith("driver/$platform/")
                    }
                    return false
                }
                override fun transform(context: TransformerContext) = Unit
                override fun hasTransformedResource() = false
                override fun modifyOutputStream(os: ZipOutputStream, preserveFileTimestamps: Boolean) = Unit
            })
            mergeServiceFiles()
        }

        val buildExecutable by tasks.getting
        val buildExecutableForPlatform = tasks.register("buildExecutableFor${suffix}") {
            configureBuildExecutable(shadowJarForPlatform.flatMap(ShadowJar::getArchiveFile), platform)
        }
        shadowJarForPlatform.configure {
            finalizedBy(buildExecutableForPlatform)
        }
        buildExecutable.dependsOn(buildExecutableForPlatform)
        shadowJar.dependsOn(shadowJarForPlatform)
    }
}

fun Task.configureBuildExecutable(archive: Provider<RegularFile>, platform: String = "") {
    group = "distribution"
    description = "Build self-contained executable CLI${if (platform.isNotBlank()) " for $platform" else ""}"

    val bin = if (project != rootProject) {
        "${rootProject.name}-${project.name}"
    } else if (platform.isBlank()) {
        project.name
    } else {
        "${project.name}-$platform"
    }

    doLast {
        layout.buildDirectory.get().dir("executable").dir(bin).asFile.apply {
            parentFile.mkdirs()
            outputStream().use { out ->
                rootProject.file("launcher.sh").inputStream().copyTo(out)
                archive.get().asFile.inputStream().copyTo(out)
            }
            setExecutable(true, false)
        }
    }
}

fun String.toCamelCase(lowercaseFirst: Boolean = false): String {
    val parts = split('-', '_')
        .filter { it.isNotEmpty() }
        .map { it.replaceFirstChar { c -> c.uppercaseChar() } }

    return if (lowercaseFirst && parts.isNotEmpty()) {
        parts[0].replaceFirstChar { it.lowercaseChar() } + parts.drop(1).joinToString("")
    } else {
        parts.joinToString("")
    }
}
