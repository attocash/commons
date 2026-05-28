import org.jetbrains.kotlin.gradle.dsl.JsSourceMapEmbedMode

group = "cash.atto"
description = "Internal JavaScript bundle used to keep split npm package modules ABI-compatible."

repositories {
    mavenCentral()
    mavenLocal()
}

kotlin {
    js(IR) {
        binaries.library()

        useEsModules()

        nodejs()

        compilerOptions {
            target = "es2015"
            useEsClasses = true
            sourceMap = true
            sourceMapEmbedSources = JsSourceMapEmbedMode.SOURCE_MAP_SOURCE_CONTENT_ALWAYS
            freeCompilerArgs.addAll(
                "-Xes-long-as-bigint",
                "-Xenable-suspend-function-exporting",
            )
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":commons-core"))
                api(project(":commons-node"))
                api(project(":commons-node-remote"))
                api(project(":commons-test"))
                api(project(":commons-wallet"))
                api(project(":commons-worker"))
                api(project(":commons-worker-remote"))
                api(project(":commons-worker-web"))
            }
        }
    }
}
