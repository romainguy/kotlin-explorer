@file:Suppress("UnstableApiUsage")

plugins {
    id("java")
}

group = "dev.romainguy.kotlin.explorer.code"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.rsyntaxtextarea)
    implementation(libs.rstaui)
}

java {
    toolchain {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(17)
    }
}