plugins {
    kotlin("jvm") version "2.3.0"
    id("org.jetbrains.kotlin.formver") version "0.1.0-SNAPSHOT"
}

repositories {
    mavenLocal {
        content {
            includeGroup("org.jetbrains.kotlin.formver")
        }
    }
    mavenCentral()
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        allWarningsAsErrors = true
    }

    sourceSets.main {
        kotlin.srcDir("../../commons-core/src/commonMain/kotlin")
        kotlin.include("cash/atto/commons/utils/Base32.kt", "cash/atto/commons/utils/Base32Proof.kt")
    }
}
