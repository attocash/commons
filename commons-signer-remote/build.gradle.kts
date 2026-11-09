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

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":commons-worker"))
                api(libs.ktor.http)
                implementation(project(":commons-transport"))

                implementation(libs.kotlin.logging)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.ktor.server.cio)
                implementation(libs.ktor.server.content.negotiation)
                implementation(libs.ktor.server.status.pages)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }

        val jvmMain by getting

        val jvmTest by getting {
            dependencies {
            }
        }
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
    "Atto Commons Signer Remote",
    "Atto Commons Signer remote provide client for signer application.",
)
