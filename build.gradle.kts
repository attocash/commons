plugins {
    val kotlinVersion = "2.1.20"
    id("org.jetbrains.kotlin.multiplatform") version kotlinVersion apply false
    id("org.jetbrains.kotlin.native.cocoapods") version kotlinVersion apply false

    id("org.jetbrains.dokka") version "2.0.0"
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
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

    tasks
        .matching {
            it.name == "publishJvmPublicationToSonatypeRepository" || it.name == "publishJsPublicationToSonatypeRepository" ||
                it.name == "publishWasmJsPublicationToSonatypeRepository"
        }.configureEach {
            dependsOn(tasks.named("signKotlinMultiplatformPublication"))
            dependsOn(tasks.named("signJvmPublication"))
            if (project.name != "commons-signer-remote" && project.name != "commons-worker-opencl" &&
                project.name != "commons-gatekeeper-test" && project.name != "commons-node-test" &&
                project.name != "commons-worker-test"
            ) {
                dependsOn(tasks.named("signJsPublication"))
                dependsOn(tasks.named("signWasmJsPublication"))
            }
        }

    tasks.matching { it.name == "publishKotlinMultiplatformPublicationToSonatypeRepository" }.configureEach {
        dependsOn(tasks.named("signJvmPublication"))
        if (project.name != "commons-signer-remote" && project.name != "commons-worker-opencl" &&
            project.name != "commons-gatekeeper-test" && project.name != "commons-node-test" &&
            project.name != "commons-worker-test"
        ) {
            dependsOn(tasks.named("signJsPublication"))
            dependsOn(tasks.named("signWasmJsPublication"))
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
