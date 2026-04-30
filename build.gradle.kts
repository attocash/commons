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

val installPuppeteerBrowsers by tasks.registering(Exec::class) {
    group = "verification"
    description = "Installs the Chromium browser used by Puppeteer-backed Karma tests."

    dependsOn("kotlinNpmInstall")

    doFirst {
        val installScript =
            rootProject.layout.buildDirectory
                .file("js/node_modules/puppeteer/install.mjs")
                .get()
                .asFile
        require(installScript.isFile) {
            "Puppeteer install script was not found at ${installScript.absolutePath}."
        }
        commandLine("node", installScript.absolutePath)
    }
}

allprojects {
    val jvmOnlyPublicationProjects = setOf("commons-signer-remote", "commons-worker-opencl", "commons-gatekeeper-test")
    val webOnlyPublicationProjects = setOf("commons-js", "commons-worker-webgpu")

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

    project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
        project.extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>("kotlin") {
            sourceSets.matching { it.name == "jsTest" || it.name == "wasmJsTest" }.configureEach {
                dependencies {
                    implementation(devNpm("puppeteer", "24.15.0"))
                }
            }
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest>().configureEach {
        if (name.endsWith("BrowserTest")) {
            dependsOn(rootProject.tasks.named("installPuppeteerBrowsers"))
        }

        fun configureRootKarmaDirectory(framework: org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTestFramework?) {
            if (framework is org.jetbrains.kotlin.gradle.targets.js.testing.karma.KotlinKarma) {
                framework.useConfigDirectory(rootProject.file("karma.config.d"))
            }
        }

        configureRootKarmaDirectory(testFramework)
        onTestFrameworkSet(
            object : org.gradle.api.Action<org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTestFramework> {
                override fun execute(framework: org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTestFramework) {
                    configureRootKarmaDirectory(framework)
                }
            },
        )
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
            if (project.name !in webOnlyPublicationProjects) {
                dependsOn(tasks.named("signJvmPublication"))
            }
            if (project.name !in jvmOnlyPublicationProjects) {
                dependsOn(tasks.named("signJsPublication"))
            }
            if (project.name !in jvmOnlyPublicationProjects) {
                dependsOn(tasks.named("signWasmJsPublication"))
            }
        }

    tasks.matching { it.name == "publishKotlinMultiplatformPublicationToSonatypeRepository" }.configureEach {
        if (project.name == "commons-js") {
            return@configureEach
        }

        dependsOn(tasks.named("signKotlinMultiplatformPublication"))
        if (project.name !in webOnlyPublicationProjects) {
            dependsOn(tasks.named("signJvmPublication"))
        }
        if (project.name !in jvmOnlyPublicationProjects) {
            dependsOn(tasks.named("signJsPublication"))
        }
        if (project.name !in jvmOnlyPublicationProjects) {
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
