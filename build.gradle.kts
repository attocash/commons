import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    val kotlinVersion = "2.0.20"

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
        languageVersion = JavaLanguageVersion.of(11)
    }
    registerFeature("opencl") {
        usingSourceSet(sourceSets["main"])
    }
    registerFeature("json") {
        usingSourceSet(sourceSets["main"])
    }
}

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
    val kotlinxSerializationVersion = "1.7.1"
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    api("org.jetbrains.kotlinx:kotlinx-io-core:0.5.3")
    api("org.bouncycastle:bcprov-jdk18on:1.78.1")

    "jsonApi"("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationVersion")
    "openclApi"("org.jocl:jocl:2.0.5")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")

    "benchmarksImplementation"("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.12")
    "benchmarksImplementation"("org.jocl:jocl:2.0.5")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
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

val sourcesJar by tasks.creating(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].allSource)
}

val javadocJar by tasks.creating(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.named("javadoc"))
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
            groupId = "cash.atto"
            artifactId = "commons"

            artifact(sourcesJar)
            artifact(javadocJar)

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
    val shouldSign = project.findProperty("signing.skip")?.toString()?.toBoolean() != true
    if (shouldSign) {
        val signingKey: String? by project
        useInMemoryPgpKeys(signingKey, "")
        sign(publishing.publications["maven"])
    }
}
