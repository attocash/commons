import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    signing
}

group = "cash.atto"
description = "Framework-independent server sessions for the Atto node stream protocol."

repositories {
    mavenCentral()
    mavenLocal()
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    api(project(":commons-node"))
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test { useJUnit() }

tasks.register("allTests") {
    group = "verification"
    description = "Runs the framework-independent stream server contract tests."
    dependsOn(tasks.test)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}

configureCommonsMavenPublication(
    "Atto Commons Node Stream Server",
    "Atto node stream server sessions, membership, acknowledgements, and ordered output without a server framework dependency.",
)
