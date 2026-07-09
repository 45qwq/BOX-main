plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.parcelize")
    kotlin("kapt")
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.fongmi.android.tv"

    compileSdk = 36
    flavorDimensions += listOf("mode", "abi")

    signingConfigs {
        create("release") {
            storeFile = file("../keystore/release.jks")
            storePassword = "xmbox123"
            keyAlias = "xmbox"
            keyPassword = "xmbox123"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    defaultConfig {
        applicationId = "com.fongmi.android.tv"
        minSdk = 24
        targetSdk = 36
        versionCode = 315
        versionName = "3.1.5"

        val githubToken = project.findProperty("GITHUB_TOKEN") ?: ""
        buildConfigField("String", "GITHUB_TOKEN", "\"${githubToken}\"")

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
                arguments["eventBusIndex"] = "com.fongmi.android.tv.event.EventIndex"
            }
        }
    }

    productFlavors {
        create("mobile") {
            dimension = "mode"
        }
        create("tablet") {
            dimension = "mode"
        }
        create("arm64_v8a") {
            dimension = "abi"
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
        create("armeabi_v7a") {
            dimension = "abi"
            ndk {
                abiFilters += "armeabi-v7a"
            }
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    packaging {
        resources {
            excludes += "META-INF/beans.xml"
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
        jniLibs {
            pickFirsts += listOf(
                "**/libavcodec.so",
                "**/libavdevice.so",
                "**/libavfilter.so",
                "**/libavformat.so",
                "**/libavutil.so",
                "**/libmedia3ext.so",
                "**/libpostproc.so",
                "**/libquickjs-android-wrapper.so",
                "**/libswresample.so",
                "**/libswscale.so"
            )
        }
    }

    android.applicationVariants.configureEach {
        outputs.configureEach {
            // outputFileName is removed in AGP 8.x, use archivesBaseName instead
        }
    }

    configurations.configureEach {
        resolutionStrategy {
            force("com.squareup.okhttp3:okhttp:${libs.versions.okhttp.get()}")
        }
    }

    lint {
        disable += "UnsafeOptInUsageError"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    kapt {
        correctErrorTypes = true
    }
}

dependencies {
    implementation(fileTree("libs") { include("*.aar") })
    implementation(project(":catvod"))
    implementation(libs.kotlin.stdlib)
    implementation(project(":forcetech"))
    implementation(project(":hook"))
    implementation(project(":jianpian"))
    implementation(project(":quickjs"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.media)
    implementation(libs.media3.common)
    implementation(libs.media3.container)
    implementation(libs.media3.database)
    implementation(libs.media3.datasource)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.datasource.rtmp)
    implementation(libs.media3.decoder)
    implementation(libs.media3.effect)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.exoplayer.smoothstreaming)
    implementation(libs.media3.extractor)
    implementation(libs.media3.ui)
    implementation(libs.room.runtime)
    implementation(libs.custom.crash)
    implementation(libs.nextlib.media3ext)
    implementation(libs.md.colors)
    implementation(libs.glide)
    implementation(libs.glide.annotations)
    implementation(libs.glide.avif) {
        exclude(group = "org.aomedia.avif.android", module = "avif")
    }
    implementation(libs.glide.okhttp3)
    implementation(libs.textdrawable)
    implementation(libs.sardine)
    implementation(libs.newpipe.extractor)
    implementation(libs.material)
    implementation(libs.zxing.core)
    implementation(libs.permissionx)
    implementation(libs.smbj)
    implementation(libs.rtmp.client)
    implementation(libs.avif.android)
    implementation(libs.cling.core)
    implementation(libs.cling.support)
    implementation(libs.eventbus)
    implementation(libs.simple.xml) {
        exclude(group = "stax", module = "stax-api")
        exclude(group = "xpp3", module = "xpp3")
    }
    "mobileImplementation"(libs.androidx.biometric)
    "mobileImplementation"(libs.androidx.swiperefreshlayout)
    "mobileImplementation"(libs.androidx.flexbox)
    "mobileImplementation"(libs.zxing.embedded) {
        isTransitive = false
    }
    "tabletImplementation"(libs.androidx.biometric)
    "tabletImplementation"(libs.androidx.swiperefreshlayout)
    "tabletImplementation"(libs.androidx.flexbox)
    "tabletImplementation"(libs.zxing.embedded) {
        isTransitive = false
    }
    implementation(libs.work.runtime.ktx)
    kapt(libs.room.compiler)
    kapt(libs.glide.compiler)
    kapt(libs.eventbus.processor)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.lottie)
    implementation(project(":danmaku"))
    implementation(project(":dlna-dmc"))
    implementation(project(":dlna-dmr"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
}
