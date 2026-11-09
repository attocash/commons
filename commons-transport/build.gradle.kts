import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.serialization)

    id("maven-publish")
    signing
}

group = "cash.atto"
description = "Shared HTTP transport for Atto Commons remote clients."
apply(plugin = "org.jetbrains.kotlin.npm-publish")

repositories {
    mavenCentral()
    mavenLocal()
}

kotlin {
    jvmToolchain(17)

    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_1_8
        }
    }

    js(IR) {
        binaries.library()
        useEsModules()
        browser {
            testTask {
                useKarma {
                    useChromeHeadlessNoSandbox()
                }
            }
        }
        nodejs()
        generateTypeScriptDefinitions()

        compilations["main"].packageJson {
            customField("name", "@attocash/commons-transport")
        }

        compilerOptions {
            target = "es2015"
            useEsClasses = true
            sourceMap = true
            sourceMapEmbedSources = org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS
            freeCompilerArgs.addAll(
                "-Xes-long-as-bigint",
                "-Xenable-suspend-function-exporting",
            )
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask {
                useKarma {
                    useChromeHeadlessNoSandbox()
                }
            }
        }
        nodejs()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":commons-core"))

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.ktor.client.cio)
            }
        }
        val jsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
}

configureCommonsMavenPublication(
    "Atto Commons Transport",
    project.description,
) {
    developers {
        developer {
            id.set("atto")
            name.set("Atto Team")
            email.set("felipe@rotilho.com")
            organization.set("Atto")
            organizationUrl.set("https://atto.cash")
        }
    }
}
