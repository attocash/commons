import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlinx.benchmark)

    id("maven-publish")
    signing
}

group = "cash.atto"
description = "Wallet utilities for deriving accounts, opening accounts, sending funds, and receiving funds."
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
                enabled = false
            }
        }

        nodejs {
            testTask {
                enabled = true
                useMocha {
                    timeout = "60000"
                    environment("MOCHA_OPTIONS", "--bail --exit")
                }
            }
        }

        generateTypeScriptDefinitions()

        compilations["main"].packageJson {
            customField("name", "@attocash/commons-wallet")
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
        browser {
            testTask {
                enabled = false
            }
        }
        nodejs {
            testTask {
                enabled = true
            }
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":commons-core"))
                api(project(":commons-node"))
                api(project(":commons-node-remote"))
                api(project(":commons-worker-remote"))

                api(libs.kotlinx.coroutines.core)

                implementation(libs.kotlin.logging)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":commons-test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting

        val jvmTest by getting {
            dependencies {
                implementation(libs.java.jwt)

                implementation(libs.slf4j.simple)
            }
        }

        val jsMain by getting

        val jsTest by getting {
            dependencies {
                implementation(npm("jsonwebtoken", "9.0.2"))
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

//
// benchmark {
//    targets {
//        register("benchmarks")
//    }
// }

configureCommonsMavenPublication(
    "Atto Commons Wallet",
    "Atto Commons Wallet provides simple wallet implementation.",
)
