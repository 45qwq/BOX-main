plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "master.flame.danmaku"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
