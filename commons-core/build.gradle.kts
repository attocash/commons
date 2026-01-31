import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Duration

plugins {
    alias(libs.plugins.kotlin.serialization)

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

        browser {
            testTask {
                useKarma {
                    timeout = Duration.ofSeconds(60)
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
            customField("name", "@attocash/commons-core")
        }

        compilerOptions {
            target = "es2015"
            useEsClasses = true
            sourceMap = true
            sourceMapEmbedSources = org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS
            freeCompilerArgs.addAll(
                // https://kotlinlang.org/docs/whatsnew20.html#per-file-compilation-for-kotlin-js-projects
                "-Xir-per-file",
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
                    timeout = Duration.ofSeconds(60)
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
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.datetime)
                api(libs.kotlinx.io.core)
                api(libs.kotlinx.serialization.core)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":commons-worker"))
                implementation(kotlin("test"))
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.serialization.protobuf)
                implementation(libs.kotlinx.coroutines.test)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.bouncycastle)
                implementation(libs.swagger.annotations)
            }
        }
        val jvmTest by getting {
            dependencies {
            }
        }

        val stablelibVersion = libs.versions.stablelib.get()

        val jsMain by getting {
            dependencies {
                implementation(npm("@stablelib/sha256", stablelibVersion))
                implementation(npm("@stablelib/sha512", stablelibVersion))
                implementation(npm("@stablelib/blake2b", stablelibVersion))
                implementation(npm("@stablelib/hmac", stablelibVersion))
                implementation(npm("@stablelib/ed25519", stablelibVersion))
            }
        }

        val jsTest by getting {
            dependencies {
            }
        }

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.kotlinx.browser)

                implementation(npm("@stablelib/sha256", stablelibVersion))
                implementation(npm("@stablelib/sha512", stablelibVersion))
                implementation(npm("@stablelib/blake2b", stablelibVersion))
                implementation(npm("@stablelib/hmac", stablelibVersion))
                implementation(npm("@stablelib/ed25519", stablelibVersion))
            }
        }

        val wasmJsTest by getting {
            dependencies {
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
            name.set("Atto Commons Core")
            description.set(
                "Atto Commons Core provides a set of low-level operations that includes signing, " +
                    "seed generation, block hashing, and account creation.",
            )
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
