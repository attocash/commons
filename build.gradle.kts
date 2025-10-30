plugins {
    val kotlinVersion = "2.2.21"
    id("org.jetbrains.kotlin.multiplatform") version kotlinVersion apply false
    id("org.jetbrains.kotlin.native.cocoapods") version kotlinVersion apply false
    id("org.jetbrains.kotlin.plugin.spring") version kotlinVersion apply false
    id("org.jetbrains.kotlin.plugin.serialization") version kotlinVersion apply false

    id("org.jetbrains.dokka") version "2.1.0"
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
    id("org.jlleitschuh.gradle.ktlint") version "12.3.0"
}

group = "cash.atto"

repositories {
    mavenCentral()
    mavenLocal()
}
allprojects {
    apply {
        if (name != "commons-spring-boot-starter") {
            apply(plugin = "org.jetbrains.kotlin.multiplatform")
        }
        plugin("org.jetbrains.dokka")
        plugin("org.jlleitschuh.gradle.ktlint")
    }

    project.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        project.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().version = "24.3.0"
    }

    tasks
        .matching {
            it.name == "publishJvmPublicationToSonatypeRepository" || it.name == "publishJsPublicationToSonatypeRepository" ||
                it.name == "publishWasmJsPublicationToSonatypeRepository"
        }.configureEach {
            if (project.name == "commons-js") {
                return@configureEach
            }

            dependsOn(tasks.named("signKotlinMultiplatformPublication"))
            dependsOn(tasks.named("signJvmPublication"))
            if (project.name != "commons-signer-remote" && project.name != "commons-worker-opencl" &&
                project.name != "commons-gatekeeper-test"
            ) {
                dependsOn(tasks.named("signJsPublication"))
                dependsOn(tasks.named("signWasmJsPublication"))
            }
        }

    tasks.matching { it.name == "publishKotlinMultiplatformPublicationToSonatypeRepository" }.configureEach {
        if (project.name == "commons-js") {
            return@configureEach
        }

        dependsOn(tasks.named("signJvmPublication"))
        if (project.name != "commons-signer-remote" && project.name != "commons-worker-opencl" &&
            project.name != "commons-gatekeeper-test"
        ) {
            dependsOn(tasks.named("signJsPublication"))
            dependsOn(tasks.named("signWasmJsPublication"))
        }
    }
}

nexusPublishing {
    repositories {
        sonatype {
            nexusUrl.set(uri("https://ossrh-staging-api.central.sonatype.com/service/local/"))
            snapshotRepositoryUrl.set(uri("https://central.sonatype.com/repository/maven-snapshots/"))
        }
    }
}
