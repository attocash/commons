import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "2.0.0"

    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("maven-publish")
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("org.jetbrains.kotlinx.benchmark") version "0.4.11"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"

    signing
}

group = "cash.atto"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

java.sourceCompatibility = JavaVersion.VERSION_1_9
java.targetCompatibility = JavaVersion.VERSION_1_9

configurations {
    val benchmarksCompile by creating {
        extendsFrom(configurations["implementation"])
    }

    val benchmarksRuntime by creating {
        extendsFrom(configurations["runtimeOnly"])
    }
    val benchmarksClasspath by creating {
        extendsFrom(benchmarksCompile, benchmarksRuntime)
    }
}

sourceSets {
    create("benchmarks") {
        kotlin {
            srcDir("src/benchmarks/kotlin")
        }
        resources.srcDir("src/benchmarks/resources")
        compileClasspath += configurations["benchmarksClasspath"] + sourceSets["main"].output
        runtimeClasspath += configurations["benchmarksClasspath"] + sourceSets["main"].output
    }
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    val kotlinxSerializationVersion = "1.7.0"
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")

    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$kotlinxSerializationVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    "benchmarksImplementation"("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.11")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_9)
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

benchmark {
    targets {
        register("benchmarks")
    }
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
            groupId = "cash.atto"
            artifactId = "commons"

            pom {
                name.set("Atto Commons")
                description.set(
                    "Atto Commons provides a set of low-level operations that includes signing, " +
                        "seed generation, block hashing, and account creation.",
                )
                url.set("https://github.com/attocash/commons")

                licenses {
                    license {
                        name.set("BSD 3-Clause License")
                        url.set("https://github.com/attocash/commons/blob/main/LICENSE")
                    }
                }

                developers {
                    developer {
                        id.set("rotilho")
                        name.set("Felipe Rotilho")
                        email.set("felipe@atto.cash")
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
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}

signing {
    val signingKey: String? by project
    useInMemoryPgpKeys(signingKey, "")
    sign(publishing.publications["maven"])
}
