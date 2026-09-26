plugins {
    kotlin("multiplatform") version "2.4.20" apply false
    kotlin("android") version "2.4.20" apply false
    kotlin("plugin.compose") version "2.4.20" apply false
    id("com.android.application") version "8.13.0" apply false
    id("com.android.library") version "8.13.0" apply false
    id("org.jetbrains.compose") version "1.10.0" apply false
}

allprojects {
    group = "com.neoworksuite.neocanvas"
    version = "1.0.0"
}
