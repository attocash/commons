import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.benchmark)

    id("maven-publish")
    signing
}

group = "cash.atto"

repositories {
    mavenCentral()
    mavenLocal()
}

kotlin {
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.get().compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    jvmToolchain(17)

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
        }
    }

    js(IR) {
        binaries.library()

        useEsModules()

        nodejs()

        generateTypeScriptDefinitions()

        compilations["main"].packageJson {
            customField("name", "@attocash/commons-test")
        }

        compilerOptions {
            target = "es2015"
            useEsClasses = true
            sourceMap = true
            sourceMapEmbedSources = org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS
            freeCompilerArgs.addAll(
                // https://kotlinlang.org/docs/whatsnew20.html#per-file-compilation-for-kotlin-js-projects

                "-Xes-long-as-bigint",
                "-Xenable-suspend-function-exporting",
            )
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {

                api(project(":commons-core"))
                api(project(":commons-node-remote"))
                api(project(":commons-worker-remote"))
                api(kotlin("test"))
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.coroutines.test)

                implementation(libs.kotlin.logging)

                implementation(libs.ktor.client.logging)
                implementation(libs.ktor.serialization)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.ktor.client.content.negotiation)

                implementation(libs.ktor.server.content.negotiation)
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation(libs.testcontainers.junit.jupiter)
                implementation(libs.testcontainers.mysql)
                implementation(libs.mysql.connector)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(npm("testcontainers", "11.7.1"))
                implementation(npm("@testcontainers/mysql", "11.7.1"))
            }
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(npm("testcontainers", "11.7.1"))
                implementation(npm("@testcontainers/mysql", "11.7.1"))
            }
        }
    }
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaGenerateHtml"))
}

publishing {
    publications.withType<MavenPublication> {
        artifact(javadocJar)

        pom {
            name.set("Atto Commons Test")
            description.set("Test utilities and mock implementations for the Atto node and work server.")
            url.set("https://github.com/attocash/commons")

            organization {
                name.set("Atto")
                url.set("https://atto.cash")
            }

            licenses {
                license {
                    name.set("BSD 3-Clause License")
                    url.set("https://github.com/attocash/commons/blob/main/LICENSE")
                }
            }

            developers {
                developer {
                    id.set("atto")
                    name.set("Atto Team")
                }
            }

            scm {
                connection.set("scm:git:git://github.com/attocash/commons.git")
                developerConnection.set("scm:git:ssh://github.com/attocash/commons.git")
                url.set("https://github.com/attocash/commons")
            }
        }
    }
}

signing {
    val shouldSign = project.findProperty("signing.skip")?.toString()?.toBoolean() != true
    if (shouldSign) {
        val signingKey: String? by project
        useInMemoryPgpKeys(signingKey, "")
        sign(publishing.publications)
    }
}
