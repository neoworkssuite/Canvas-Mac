import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvm() {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        jvmTest.dependencies { implementation(kotlin("test")) }
        jvmMain.dependencies {
            implementation(project(":core"))
            implementation(project(":brushes"))
            implementation(project(":ui"))
            implementation(project(":renderer"))
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.neoworksuite.neocanvas.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "NeoCanvas"
            packageVersion = "1.0.0"
            vendor = "NeoWorksSuite"
            fileAssociation(
                mimeType = "application/x-neocanvas",
                extension = "neocanvas",
                description = "NeoCanvas Document",
                windowsIconFile = rootProject.file("assets/branding/neocanvas.ico"),
            )
            windows {
                iconFile.set(rootProject.file("assets/branding/neocanvas.ico"))
                menuGroup = "NeoWorksSuite"
                shortcut = true
                menu = true
                upgradeUuid = "A9E2E7D8-0F2E-4D85-A6A8-7A2FBA9E71C1"
            }
        }
    }
}
