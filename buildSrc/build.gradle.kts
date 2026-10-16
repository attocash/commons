plugins {
    kotlin("jvm") version "2.4.21"
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(gradleApi())
    implementation(localGroovy())
}
