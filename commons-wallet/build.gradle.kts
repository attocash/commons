plugins {
    val kotlinVersion = "2.0.21"
    kotlin("plugin.serialization") version "1.7.0"
    kotlin("plugin.allopen") version kotlinVersion

    id("org.jetbrains.kotlinx.benchmark") version "0.4.12"
}

group = "cash.atto"

repositories {
    mavenCentral()
    mavenLocal()
}


kotlin {
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.get().compilerOptions {
                freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    jvmToolchain(11)

    jvm()

    applyDefaultHierarchyTemplate()

    sourceSets {
        val ktorVersion = "2.3.12"
        val commonMain by getting {
            dependencies {
                api(project(":commons-core"))
                api(project(":commons-worker"))
                api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

                implementation("io.github.oshai:kotlin-logging:7.0.0")

                implementation("io.ktor:ktor-client-cio:$ktorVersion")
                implementation("io.ktor:ktor-client-logging:$ktorVersion")
                implementation("io.ktor:ktor-serialization:$ktorVersion")
                implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
                implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-server-cio:$ktorVersion")
                implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")

            }
        }
        val jvmMain by getting {
            dependencies {
                runtimeOnly("io.ktor:ktor-client-okhttp-jvm:$ktorVersion")
                implementation("com.auth0:java-jwt:4.4.0")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation("org.slf4j:slf4j-simple:2.0.16")
            }
        }
    }
}

benchmark {
    targets {
        register("jvm")
    }
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    targets {
        register("benchmarks")
    }
}
