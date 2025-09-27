plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
rootProject.name = "commons"
include("commons-core")
include("commons-gatekeeper")
include("commons-gatekeeper-test")
include("commons-node")
include("commons-node-remote")
include("commons-node-test")
include("commons-signer-remote")
include("commons-spring-boot-starter")
include("commons-wallet")
include("commons-worker")
include("commons-worker-opencl")
include("commons-worker-remote")
include("commons-worker-test")
include("commons-js")
