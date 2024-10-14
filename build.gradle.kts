plugins {
    val kotlinVersion = "2.0.21"
    id("org.jetbrains.kotlin.multiplatform") version kotlinVersion apply false
    id("org.jetbrains.kotlin.native.cocoapods") version kotlinVersion apply false

    id("maven-publish")
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"

    signing
}

group = "cash.atto"

repositories {
    mavenCentral()
    mavenLocal()
}

subprojects {
    apply {
        plugin("org.jetbrains.kotlin.multiplatform")
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
        publishing.publications.forEach { publication ->
            sign(publication)
        }
    }
}

publishing {
    publications {
        subprojects.forEach { subproject ->
            subproject.plugins.withId("maven-publish") {
                register<MavenPublication>(subproject.name) {
                    from(subproject.components["java"])

                    groupId = "cash.atto"
                    artifactId = subproject.name
                    version = "1.0.0"

                    pom {
                        name.set("Atto ${subproject.name.uppercase()}")
                        description.set("This is the ${subproject.name} module of Atto.")
                        url.set("https://github.com/attocash/commons")

                        licenses {
                            license {
                                name.set("BSD 3-Clause License")
                                url.set("https://github.com/attocash/commons/LICENSE")
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
    }
}
