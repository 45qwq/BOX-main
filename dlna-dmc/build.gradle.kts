plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.android.cast.dlna.dmc"
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
    api(project(":dlna-core"))
    implementation("org.eclipse.jetty:jetty-server:8.1.21.v20160908")
    implementation("org.eclipse.jetty:jetty-servlet:8.1.21.v20160908")
    implementation("org.eclipse.jetty:jetty-client:8.1.21.v20160908")
    implementation("javax.servlet:javax.servlet-api:3.1.0")
}
