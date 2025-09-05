import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    val kotlinVersion = "2.1.21"
    kotlin("plugin.serialization") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion

    id("org.jetbrains.kotlinx.benchmark") version "0.4.14"
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

    jvmToolchain(8)

    jvm()

    js(IR) {
        binaries.library()

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
            customField("name", "@attocash/commons-js")

            customField("main", "index.mjs") // CommonJS entry
            customField("module", "index.mjs") // ESM entry
            customField("types", "index.d.ts") // TypeScript types
        }

        compilerOptions {
            target = "es2015"
            useEsClasses = true
            freeCompilerArgs.addAll(
                // https://kotlinlang.org/docs/whatsnew20.html#per-file-compilation-for-kotlin-js-projects
                "-Xir-per-file",
            )
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        compilerOptions {
            freeCompilerArgs.add("-Xwasm-attach-js-exception")
        }
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
                api(project(":commons-core"))
                api(project(":commons-node"))
                api(project(":commons-node-remote"))
                api(project(":commons-worker"))
                api(project(":commons-worker-remote"))
            }
        }
        val jsMain by getting
        val jsTest by getting
    }
}

// benchmark {
//    targets {
//        register("jvm")
//    }
// }

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

// benchmark {
//    targets {
//        register("benchmarks")
//    }
// }
