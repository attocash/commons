plugins {
    val kotlinVersion = "2.1.0"
    id("org.jetbrains.kotlin.multiplatform") version kotlinVersion apply false
    id("org.jetbrains.kotlin.native.cocoapods") version kotlinVersion apply false

    id("org.jetbrains.dokka") version "2.0.0"
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
}

group = "cash.atto"

repositories {
    mavenCentral()
    mavenLocal()
}

subprojects {
    apply {
        plugin("org.jetbrains.kotlin.multiplatform")
        plugin("org.jetbrains.dokka")
        plugin("org.jlleitschuh.gradle.ktlint")
    }

    tasks.matching { it.name == "publishJvmPublicationToSonatypeRepository" }.configureEach {
        dependsOn(tasks.named("signKotlinMultiplatformPublication"))
    }

    tasks.matching { it.name == "publishJsPublicationToSonatypeRepository" }.configureEach {
        dependsOn(tasks.named("signKotlinMultiplatformPublication"))
    }

    tasks.matching { it.name == "publishKotlinMultiplatformPublicationToSonatypeRepository" }.configureEach {
        dependsOn(tasks.named("signJvmPublication"))
        dependsOn(tasks.named("signJsPublication"))
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
