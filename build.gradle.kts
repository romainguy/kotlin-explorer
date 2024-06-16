@file:Suppress("UnstableApiUsage")

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo

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

version = "1.4.3"

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

            packageVersion = version.toString()
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

val currentArch: String = when (val osArch = System.getProperty("os.arch")) {
    "x86_64", "amd64" -> "x64"
    "aarch64" -> "arm64"
    else -> error("Unsupported OS arch: $osArch")
}

/**
 * TODO: workaround for https://github.com/JetBrains/compose-multiplatform/issues/4976.
 */
val renameDmg by tasks.registering {
    group = "distribution"
    description = "Rename the DMG file"
    val packageDmg = tasks.named<AbstractJPackageTask>("packageDmg")
    dependsOn(packageDmg)

    doLast {
        // build/compose/binaries/main/dmg/*.dmg
        val targetFile = packageDmg.get().appImage.get().dir("../dmg").asFile.toPath()
            .listDirectoryEntries("*.dmg").single()
        check(targetFile.exists()) {
            "The DMG file does not exist: $targetFile"
        }
        targetFile.moveTo(targetFile.resolveSibling("kotlin-explorer-$currentArch-$version.dmg"))
    }
}

tasks.build {
    dependsOn(renameDmg)
}
