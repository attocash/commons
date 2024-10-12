plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "commons"
include("commons-core")
include("commons-worker")
include("commons-worker-opencl")
