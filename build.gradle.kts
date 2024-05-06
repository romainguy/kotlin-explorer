@file:Suppress("UnstableApiUsage")

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
}

kotlin {
    jvm {
        jvmToolchain {
            vendor = JvmVendorSpec.JETBRAINS
            languageVersion = JavaLanguageVersion.of(17)
        }
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs) {
                    exclude(group = "org.jetbrains.compose.material")
                }
                implementation(libs.jewel.decorated)
                implementation(libs.jna)
                implementation(libs.skiko.mac)
                implementation(libs.rsyntaxtextarea)
                implementation(libs.compose.splitpane)
                implementation(libs.rstaui)
                runtimeOnly(libs.skiko.linux)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "dev.romainguy.kotlin.explorer.KotlinExplorerKt"
        buildTypes.release.proguard {
            configurationFiles.from(project.file("compose-desktop.pro"))
        }
        nativeDistributions {
            modules("jdk.unsupported")

            targetFormats(TargetFormat.Dmg)

            packageName = "Kotlin Explorer"
            packageVersion = "1.0.0"
            description = "Kotlin Explorer"
            vendor = "Romain Guy"
            licenseFile = rootProject.file("LICENSE")

            macOS {
                dockName = "Kotlin Explorer"
                bundleID = "dev.romainguy.kotlin.explorer"
            }
        }
    }
}
