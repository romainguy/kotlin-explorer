import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

group = "dev.romainguy.kotlin"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

kotlin {
    jvm {
        jvmToolchain(11)
        withJava()
    }
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:${extra["skiko.version"] as String}")
                implementation("org.jetbrains.compose.components:components-splitpane-desktop:${extra["compose.version"] as String}")
                implementation("com.fifesoft:rsyntaxtextarea:${extra["rsyntaxtextarea.version"] as String}")
                implementation("com.fifesoft:rstaui:${extra["rstaui.version"] as String}")
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
        }
    }
}
