plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hollandhaptics.babyapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hollandhaptics.babyapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // Newer core-ktx / appcompat releases require compileSdk 36; intentionally pinned.
        disable += "GradleDependency"
    }
}

dependencies {
    // core-ktx 1.13.1 / appcompat 1.7.0 are the latest versions compatible with
    // compileSdk 35 + AGP 8.7. Bumping further requires compileSdk 36 + AGP 8.9+.
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
}
