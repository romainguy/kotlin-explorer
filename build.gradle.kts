@file:Suppress("UnstableApiUsage")

import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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

version = "1.6.6"
val baseName = "Kotlin Explorer"
val distributionBaseName = "kotlin-explorer"

kotlin {
    jvm {
        @Suppress("OPT_IN_USAGE")
        mainRun {
            mainClass = "dev.romainguy.kotlin.explorer.KotlinExplorerKt"
        }
    }

    jvmToolchain {
        vendor = JvmVendorSpec.JETBRAINS
        languageVersion = JavaLanguageVersion.of(21)
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs) {
                    exclude(group = "org.jetbrains.compose.material")
                }
                implementation(compose.components.resources)
                implementation(libs.collection)
                implementation(libs.compose.backhandler)
                implementation(libs.compose.material3)
                implementation(libs.compose.splitpane)
                implementation(libs.jewel)
                implementation(libs.jewel.decorated)
                implementation(libs.jewel.markdown.core)
                implementation(libs.jewel.markdown.intUiStandaloneStyling)
                implementation(libs.jna)
                implementation(libs.jsoup)
                implementation(libs.lifecycle)
                implementation(libs.lifecycle.compose)
                implementation(libs.lifecycle.viewmodel)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.rsyntaxtextarea)
                implementation(libs.rstaui)
                implementation(project(":token-makers"))
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

composeCompiler {
    stabilityConfigurationFiles = listOf(layout.projectDirectory.file("compose-stability.config"))
    reportsDestination = layout.buildDirectory.dir("compose-compiler")
}

compose.desktop {
    application {
        mainClass = "dev.romainguy.kotlin.explorer.KotlinExplorerKt"
        buildTypes.release.proguard {
            configurationFiles.from(project.file("compose-desktop.pro"))
        }
        nativeDistributions {
            modules("jdk.unsupported")

            targetFormats(TargetFormat.Dmg, TargetFormat.Msi)

            packageVersion = version.toString()
            packageName = baseName
            description = baseName
            vendor = "Romain Guy"
            licenseFile = rootProject.file("LICENSE")

            macOS {
                dockName = "Kotlin Explorer"
                iconFile = file("art/app-icon/icon.icns")
                bundleID = "dev.romainguy.kotlin.explorer"
            }

            windows {
                menuGroup = "Kotlin Explorer"
                iconFile = file("art/app-icon/icon.ico")
            }
        }
    }
}

/**
 * TODO: workaround for https://github.com/JetBrains/compose-multiplatform/issues/4976.
 */
val packageAndRenameReleaseDistributionForCurrentOS by tasks.registering {
    group = "distribution"
    description = "Packages and renames release distributions for the current OS"
    dependsOn("packageReleaseDistributionForCurrentOS")
    doLast { renameReleaseDistributionForCurrentOS() }
}

private fun renameReleaseDistributionForCurrentOS() {
    val os = DefaultNativePlatform.getCurrentOperatingSystem()
    val arch = DefaultNativePlatform.getCurrentArchitecture()

    val osName = when {
        os.isMacOsX -> "macos"
        os.isWindows -> "windows"
        else -> os.internalOs.familyName
    }

    val archName = when {
        arch.isAmd64 -> "x64"
        arch.isArm64 -> "arm64"
        else -> arch.name
    }

    compose.desktop.application.nativeDistributions.targetFormats.forEach { targetFormat ->
        if (!targetFormat.isCompatibleWithCurrentOS) return@forEach

        val distributionPath = layout.buildDirectory.get()
            .dir("compose/binaries/main-release/${targetFormat.outputDirName}")
            .file("$baseName-$version${targetFormat.fileExt}")
            .asFile.toPath()

        val newDistributionPath = distributionPath
            .resolveSibling("$distributionBaseName-$osName-$archName-$version${targetFormat.fileExt}")

        Files.move(distributionPath, newDistributionPath, StandardCopyOption.REPLACE_EXISTING)
    }
}