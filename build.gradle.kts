plugins {
    val kotlinVersion = "2.0.21"
    id("org.jetbrains.kotlin.multiplatform") version kotlinVersion apply false
    id("org.jetbrains.kotlin.native.cocoapods") version kotlinVersion apply false

    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
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
