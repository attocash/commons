import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("maven-publish")
    signing
}

group = "cash.atto"
description = "Browser WebGPU and WebGL proof-of-work workers for Atto applications."
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

        generateTypeScriptDefinitions()

        compilations["main"].packageJson {
            customField("name", "@attocash/commons-worker-web")
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
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":commons-worker"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val webMain by getting
        val webTest by getting
        val jsMain by getting
        val jsTest by getting
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.browser)
            }
        }
        val wasmJsTest by getting
    }
}

configureCommonsMavenPublication(
    "Atto Commons Worker Web",
    "Atto Commons Worker provides WebGPU and WebGL proof-of-work implementations for browser JavaScript and Wasm.",
)
