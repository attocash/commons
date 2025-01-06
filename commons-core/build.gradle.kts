plugins {
    val kotlinVersion = "2.1.0"
    kotlin("plugin.serialization") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion

    id("org.jetbrains.kotlinx.benchmark") version "0.4.13"

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

    jvmToolchain(11)

    jvm()

    js(IR) {
        browser {
            testTask {
                useKarma {
                    useChromiumHeadless()
                }
            }
        }

        nodejs()
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                api("org.jetbrains.kotlinx:kotlinx-io-core:0.6.0")
                api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.13")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":commons-worker"))
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.bouncycastle:bcprov-jdk18on:1.79")
            }
        }
        val jvmTest by getting {
            dependencies {
            }
        }

        val stablelibVersion = "1.0.1"

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

val javadocJar by tasks.creating(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaHtml"))
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
            url.set("https://atto.cash")

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
