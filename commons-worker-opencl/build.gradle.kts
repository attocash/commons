import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("plugin.allopen")
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
        compilations.create("benchmark") {
            associateWith(this@jvm.compilations.getByName("main"))
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":commons-worker"))
                implementation(libs.kotlinx.benchmark.runtime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.jocl)
            }
        }
        val jvmTest by getting {
            dependencies {
            }
        }

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
    "Atto Commons Worker OpenCL",
    "Atto Commons Worker provides OpenCL GPU work implementation.",
)
