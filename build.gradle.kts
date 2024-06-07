@file:Suppress("UnstableApiUsage")

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
}

kotlin {
    jvm {
        @Suppress("OPT_IN_USAGE")
        mainRun {
            mainClass = "dev.romainguy.kotlin.explorer.KotlinExplorerKt"
        }
    }

    jvmToolchain {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(17)
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs) {
                    exclude(group = "org.jetbrains.compose.material")
                }
                implementation(libs.collection)
                implementation(libs.compose.material3)
                implementation(libs.compose.splitpane)
                implementation(libs.jewel)
                implementation(libs.jewel.decorated)
                implementation(libs.jna)
                implementation(libs.lifecycle)
                implementation(libs.lifecycle.compose)
                implementation(libs.lifecycle.viewmodel)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.skiko.mac)
                implementation(libs.rsyntaxtextarea)
                implementation(libs.rstaui)
                implementation(project(":token-makers"))
                runtimeOnly(libs.skiko.linux)
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation(libs.junit4)
                implementation(libs.kotlin.test)
                implementation(libs.truth)
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

            packageVersion = "1.4.2"
            packageName = "Kotlin Explorer"
            description = "Kotlin Explorer"
            vendor = "Romain Guy"
            licenseFile = rootProject.file("LICENSE")

            macOS {
                dockName = "Kotlin Explorer"
                iconFile = file("art/app-icon/icon.icns")
                bundleID = "dev.romainguy.kotlin.explorer"
            }
        }
    }
}
