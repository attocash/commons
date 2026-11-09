import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.spring)

    id("maven-publish")
    signing
}

group = "cash.atto"

repositories {
    mavenCentral()
    mavenLocal()
}

java {
    withSourcesJar()

    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    api(project(":commons-core"))
    api(project(":commons-node"))
    api(libs.kotlinx.coroutines.core)

    implementation(platform(libs.spring.boot.dependencies))

    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    compileOnly("org.springframework:spring-webflux")

    compileOnly(libs.springdoc.openapi.webflux)

    compileOnly("org.springframework.data:spring-data-r2dbc")

    implementation(libs.kotlinx.serialization.json)

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito")
    }
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation(libs.springdoc.openapi.webflux)
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("io.r2dbc:r2dbc-h2")
    testImplementation(libs.mockk)
}

tasks.test { useJUnitPlatform() }

tasks.register("allTests") {
    group = "verification"
    description = "Runs the Spring Boot starter tests alongside the multiplatform test suites."
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
    "Atto Commons Spring Boot Starter",
    "Atto Commons Spring Boot Starter provides the necessary configuration for Spring applications to seamlessly work with Atto primitives.",
)
