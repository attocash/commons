plugins {
    val kotlinVersion = "2.0.21"
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

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":commons-worker"))
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.13")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation("org.jocl:jocl:2.0.5")
            }
        }
        val jvmTest by getting {
            dependencies {

            }
        }
    }
}

//benchmark {
//    targets {
//        register("jvm")
//    }
//}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

//benchmark {
//    targets {
//        register("benchmarks")
//    }
//}

val javadocJar by tasks.creating(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaJavadoc"))
}

publishing {
    publications.withType<MavenPublication> {
        artifact(javadocJar)

        pom {
            name.set("Atto Commons Worker OpenCL")
            description.set(
                "Atto Commons Worker provides OpenCL GPU work implementation.",
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
