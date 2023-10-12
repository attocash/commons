import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.10"
    id("maven-publish")
    id("io.github.gradle-nexus.publish-plugin") version "1.3.0"

    signing
}

group = "cash.atto"

java {
    withJavadocJar()
    withSourcesJar()
}

java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.76")
    compileOnly("com.fasterxml.jackson.core:jackson-annotations:2.15.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    publications {
        register<MavenPublication>("maven") {
            from(components["java"])
            groupId = "cash.atto"
            artifactId = "commons"

            pom {
                name.set("Atto Commons")
                description.set("Atto Commons provides a set of low-level operations that includes signing, seed generation, block hashing, and account creation.")
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