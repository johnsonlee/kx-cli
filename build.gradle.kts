import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.github.jengelman.gradle.plugins.shadow.transformers.Transformer
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext
import io.johnsonlee.gradle.publish.publishing
import org.apache.tools.zip.ZipOutputStream
import org.gradle.api.Project.DEFAULT_VERSION

plugins {
    kotlin("jvm") version embeddedKotlinVersion
    kotlin("kapt") version embeddedKotlinVersion
    id("io.johnsonlee.sonatype-publish-plugin") version "1.10.0"
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
    implementation(libs.auto.service)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.module.jsr310)
    implementation(libs.jsonpath)
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
    mergeServiceFiles()
}

setOf("linux", "linux-arm64", "mac", "mac-arm64", "win32_x64").let { platforms ->
    platforms.forEach { platform ->
        val taskName = "shadowJarFor${platform.replace("[^a-zA-Z0-9]".toRegex(), "")}"
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

        publishing {
            publications {
                withType<MavenPublication> {
                    if (name != "shadow") return@withType
                    artifact(shadowJarForPlatform) {
                        classifier = platform
                    }
                }
            }
        }

        shadowJar.dependsOn(shadowJarForPlatform)
    }
}
