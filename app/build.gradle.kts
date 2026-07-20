import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(21)
}

android {
    dependenciesInfo {
        // Disables dependency metadata when       building APKs (for IzzyOnDroid/F-Droid)
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles (for Google Play)
        includeInBundle = false
    }
    namespace = "com.steel101.musicplayer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.steel101.musicplayer"
        minSdk = 29
        targetSdk = 37
        versionCode = 11
        versionName = "1.0.11"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a"))
        }

        val properties = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }
        val defaultKey = String(charArrayOf(83.toChar(), 65.toChar(), 111.toChar(), 89.toChar(), 79.toChar(), 101.toChar(), 100.toChar(), 56.toChar(), 84.toChar(), 101.toChar()))
        val rawKey = (System.getenv("ACOUSTID_KEY") ?: properties.getProperty("ACOUSTID_KEY") ?: defaultKey)
        val codes = mutableListOf<String>()
        for (i in 0 until rawKey.length) {
            codes.add(rawKey[i].code.toString())
        }
        val obfuscatedKey = codes.joinToString(",")
        buildConfigField("String", "ACOUSTID_KEY_DATA", "\"$obfuscatedKey\"")
    }

    androidResources {
        localeFilters += "en"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "none"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols.add("**/libfpcalc.so")
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/versions/**"
            excludes += "/META-INF/*.kotlin_module"
            excludes += "/META-INF/version-control-info.textproto"
            excludes += "/*.txt"
            excludes += "/com/google/thirdparty/**"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(libs.coil.compose)
    implementation("androidx.palette:palette-ktx:1.0.0")
    implementation("io.github.kyant0:taglib:1.0.6")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
    implementation("com.github.creati8e:fpcalc-android:v1.1.1")
    implementation("com.getkeepsafe.relinker:relinker:1.4.5")


    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}