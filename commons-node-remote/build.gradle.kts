import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.benchmark)

    id("maven-publish")
    signing
}

group = "cash.atto"
description = "Remote HTTP Atto node client for JavaScript and TypeScript applications."
apply(plugin = "org.jetbrains.kotlin.npm-publish")

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
            customField("name", "@attocash/commons-node-remote")
        }

        compilerOptions {
            target = "es2015"
            useEsClasses = true
            sourceMap = true
            sourceMapEmbedSources = org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS
            freeCompilerArgs.addAll(
                // https://kotlinlang.org/docs/whatsnew20.html#per-file-compilation-for-kotlin-js-projects
                "-Xes-long-as-bigint",
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
                api(project(":commons-node"))
                implementation(project(":commons-transport"))
                implementation(libs.ktor.http)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        val jvmMain by getting

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.websockets)
            }
        }

        val jsMain by getting

        val jsTest by getting {
            dependencies {
                implementation(npm("ws", "8.20.1"))
            }
        }

        val wasmJsTest by getting {
            dependencies {
                implementation(npm("ws", "8.20.1"))
            }
        }

        val wasmJsMain by getting
    }
}

// benchmark {
//    targets {
//        register("jvm")
//    }
// }

// benchmark {
//    targets {
//        register("benchmarks")
//    }
// }

configureCommonsMavenPublication(
    "Atto Commons Node Remote",
    "Atto Commons Remote provide client for the node.",
)
