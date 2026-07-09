plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.github.catvod.crawler"

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

dependencies {
    api(libs.androidx.annotation)
    api(libs.androidx.preference)
    api(libs.gson)
    api(libs.cronet.okhttp)
    api(libs.juniversalchardet)
    api(libs.logger)
    api(libs.okhttp)
    api(libs.okhttp.dnsoverhttps)
    api(libs.logging.interceptor)
    api(libs.cronet.embedded)
    api(libs.guava.android) {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "org.checkerframework", module = "checker-compat-qual")
        exclude(group = "org.checkerframework", module = "checker-qual")
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
        exclude(group = "com.google.j2objc", module = "j2objc-annotations")
        exclude(group = "org.codehaus.mojo", module = "animal-sniffer-annotations")
    }
}
