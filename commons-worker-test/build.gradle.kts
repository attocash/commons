plugins {
    val kotlinVersion = "2.1.21"
    kotlin("plugin.serialization") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion

    id("org.jetbrains.kotlinx.benchmark") version "0.4.14"

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

    applyDefaultHierarchyTemplate()

    sourceSets {
        val ktorVersion = "3.2.0"
        val commonMain by getting {
            dependencies {
                api(project(":commons-core"))
                api(project(":commons-gatekeeper"))
                api(project(":commons-node-test"))
                api(project(":commons-worker-remote"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

                implementation("io.github.oshai:kotlin-logging:7.0.7")

                implementation("io.ktor:ktor-client-logging:$ktorVersion")
                implementation("io.ktor:ktor-serialization:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")

                implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-server-cio:$ktorVersion")
            }
        }
    }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

val javadocJar by tasks.creating(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaHtml"))
}

publishing {
    publications.withType<MavenPublication> {
        artifact(javadocJar)

        pom {
            name.set("Atto Commons Wallet")
            description.set(
                "Atto Commons Worker Test provides mock remote work implementation.",
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
