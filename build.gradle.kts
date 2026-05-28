plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.cocoapods) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.serialization) apply false

    alias(libs.plugins.dokka)
    alias(libs.plugins.nexus.publish)
    alias(libs.plugins.npm.publish) apply false
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
    val publicationProject = this
    val jvmOnlyPublicationProjects = setOf("commons-signer-remote", "commons-worker-opencl")
    val webOnlyPublicationProjects = setOf("commons-js", "commons-worker-web")

    apply {
        if (name != "commons-spring-boot-starter" && name != rootProject.name) {
            apply(plugin = "org.jetbrains.kotlin.multiplatform")
        }
        plugin("org.jetbrains.dokka")
        plugin("org.jlleitschuh.gradle.ktlint")
    }

    project.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
        project.the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().version = libs.versions.nodejs.get()
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

    project.plugins.withId("org.jetbrains.kotlin.npm-publish") {
        val npmPackageDescription = publicationProject.moduleDescriptionProvider()

        publicationProject.extensions.configure<dev.petuska.npm.publish.extension.NpmPublishExtension>("npmPublish") {
            organization.set("attocash")
            version.set(publicationProject.provider { publicationProject.version.toString() })
            access.set(PUBLIC)

            registries {
                npmjs {}
            }

            packages.configureEach {
                if (name != "js") {
                    return@configureEach
                }

                packageName.set(publicationProject.name)
                types.set("${rootProject.name}-${publicationProject.name}.d.mts")
                readme.set(publicationProject.layout.projectDirectory.file("README.md"))

                files {
                    from(rootProject.layout.projectDirectory.file("LICENSE"))
                }

                packageJson {
                    description.set(npmPackageDescription)
                    license.set("BSD-3-Clause")
                    homepage.set("https://atto.cash")
                    keywords.addAll("atto", "kotlin", "kotlin-js", "cryptocurrency")
                    repository {
                        type.set("git")
                        url.set("git+https://github.com/attocash/commons.git")
                        directory.set(publicationProject.path.removePrefix(":"))
                    }
                    bugs {
                        url.set("https://github.com/attocash/commons/issues")
                    }
                }
            }
        }

        tasks
            .matching { it.name.startsWith("publish") && it.name.endsWith("PackageToNpmjsRegistry") }
            .configureEach {
                publicationProject.description = npmPackageDescription.get()
            }
        gradle.projectsEvaluated {
            publicationProject.description = npmPackageDescription.get()
        }

        publicationProject.configureCommonsNpmPackageExternalization()

        afterEvaluate {
            project.extensions.configure<dev.petuska.npm.publish.extension.NpmPublishExtension>("npmPublish") {
                packages.matching { it.name != "js" }.configureEach {
                    files.setFrom(emptyList<Any>())
                }
            }

            tasks
                .matching {
                    it.name == "assembleWasmJsPackage" ||
                        it.name == "packWasmJsPackage" ||
                        it.name.startsWith("publishWasmJsPackage")
                }.configureEach {
                    enabled = false
                    setDependsOn(emptyList<Any>())
                }
        }

        tasks
            .matching {
                it.name == "assembleWasmJsPackage" ||
                    it.name == "packWasmJsPackage" ||
                    it.name.startsWith("publishWasmJsPackage")
            }.configureEach {
                enabled = false
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
