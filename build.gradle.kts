@file:Suppress("UnstableApiUsage")

import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
}

java {
    toolchain {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(17)
    }
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
                implementation("org.jetbrains.jewel:jewel-int-ui-standalone:${extra["jewel.version"] as String}")
                implementation("org.jetbrains.jewel:jewel-int-ui-decorated-window:${extra["jewel.version"] as String}")
                implementation("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:${extra["skiko.version"] as String}")
                implementation("org.jetbrains.compose.components:components-splitpane-desktop:${extra["compose.version"] as String}")
                implementation("com.fifesoft:rsyntaxtextarea:${extra["rsyntaxtextarea.version"] as String}")
                implementation("com.fifesoft:rstaui:${extra["rstaui.version"] as String}")
                implementation("net.java.dev.jna:jna:${extra["jna.version"] as String}")
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
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Kotlin Explorer"
            packageVersion = "1.0.0"
            modules("jdk.unsupported")
        }
    }
}
