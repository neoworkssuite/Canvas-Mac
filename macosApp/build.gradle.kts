import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":core"))
            implementation(project(":brushes"))
            implementation(project(":renderer"))
            implementation(project(":ui"))
            implementation(compose.desktop.currentOs)
        }
        jvmTest.dependencies { implementation(kotlin("test")) }
    }
}

compose.desktop {
    application {
        mainClass = "com.neoworksuite.neocanvas.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "NeoCanvas"
            packageVersion = "1.0.0"
            vendor = "NeoWorksSuite"
            macOS {
                bundleID = "com.neoworksuite.neocanvas"
            }
        }
    }
}
