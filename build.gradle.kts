plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.cocoapods) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.serialization) apply false

    alias(libs.plugins.dokka)
    alias(libs.plugins.nexus.publish)
    alias(libs.plugins.ktlint)
}

group = "cash.atto"

repositories {
    mavenCentral()
    mavenLocal()
}
allprojects {
    apply {
        if (name != "commons-spring-boot-starter" && name != rootProject.name) {
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
            it.name == "publishJvmPublicationToSonatypeRepository" ||
                it.name == "publishJsPublicationToSonatypeRepository" ||
                it.name == "publishWasmJsPublicationToSonatypeRepository"
        }.configureEach {
            if (project.name == "commons-js") {
                return@configureEach
            }

            dependsOn(tasks.named("signKotlinMultiplatformPublication"))
            dependsOn(tasks.named("signJvmPublication"))
            if (project.name != "commons-signer-remote" &&
                project.name != "commons-worker-opencl" &&
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
        if (project.name != "commons-signer-remote" &&
            project.name != "commons-worker-opencl" &&
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
