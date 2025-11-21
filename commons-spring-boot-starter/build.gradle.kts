import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")

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
    api(project(":commons-gatekeeper"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.0"))

    compileOnly("org.springframework.boot:spring-boot-autoconfigure")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    compileOnly("org.springframework:spring-webflux")

    compileOnly("org.springdoc:springdoc-openapi-starter-webflux-ui:2.8.14")

    compileOnly("org.springframework.data:spring-data-r2dbc")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito")
    }
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    testRuntimeOnly("io.r2dbc:r2dbc-h2")
    testImplementation("io.mockk:mockk:1.14.6")
}

tasks.test { useJUnitPlatform() }

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(tasks.named("dokkaGenerateHtml"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifact(javadocJar)
            pom {
                name.set("Atto Commons Spring Boot Starter")
                description.set(
                    "Atto Commons Spring Boot Starter provides the necessary configuration for Spring applications to " +
                        "seamlessly work with Atto primitives.",
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
}

signing {
    val shouldSign = project.findProperty("signing.skip")?.toString()?.toBoolean() != true
    if (shouldSign) {
        val signingKey: String? by project
        useInMemoryPgpKeys(signingKey, "")
        sign(publishing.publications["mavenJava"])
    }
}
