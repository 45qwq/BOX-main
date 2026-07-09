plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.android.cast.dlna.core"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        targetSdk = 36
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(libs.cling.core)
    api(libs.cling.support)
    api("org.nanohttpd:nanohttpd:2.3.1")
}
