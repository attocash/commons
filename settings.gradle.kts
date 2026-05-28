plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
rootProject.name = "commons"
include("commons-core")
include("commons-node")
include("commons-node-remote")
include("commons-test")
include("commons-signer-remote")
include("commons-spring-boot-starter")
include("commons-wallet")
include("commons-worker")
include("commons-worker-opencl")
include("commons-worker-web")
include("commons-worker-remote")
include("commons-js")
include("commons-npm-bundle")
