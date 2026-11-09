import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.serialization)
    kotlin("plugin.allopen")
    alias(libs.plugins.kotlinx.benchmark)

    id("maven-publish")
    signing
}

group = "cash.atto"
description = "CPU proof-of-work worker implementation for Atto applications."
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
        compilations.create("benchmark") {
            associateWith(this@jvm.compilations.getByName("main"))
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

        nodejs {
            testTask {
                useMocha {
                    timeout = "60000"
                }
            }
        }

        generateTypeScriptDefinitions()

        compilations["main"].packageJson {
            customField("name", "@attocash/commons-worker")
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
                api(libs.kotlinx.coroutines.core)

                implementation(libs.kotlin.logging)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.bouncycastle)
            }
        }
        val jvmTest by getting

        val jsMain by getting
        val jsTest by getting

        val jvmBenchmark by getting {
            dependencies {
                implementation(libs.kotlinx.benchmark.runtime)
            }
        }
    }
}

benchmark {
    targets {
        register("jvmBenchmark")
    }
}

tasks.withType<Jar>().matching { it.name.endsWith("BenchmarkJar") }.configureEach {
    // Dependency signatures no longer match after packaging the benchmark fat jar.
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

configureCommonsMavenPublication(
    "Atto Commons Worker",
    "Atto Commons Worker provides worker interface and CPU worker implementation.",
)
