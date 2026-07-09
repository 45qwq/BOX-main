plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.fongmi.hook"

    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36
    }
}
