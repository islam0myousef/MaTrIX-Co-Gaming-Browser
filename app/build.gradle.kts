plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    buildFeatures {
        // AGP 8+ disables BuildConfig generation by default.
        buildConfig = true
    }

    namespace = "com.matrixco.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.matrixco.browser"
        minSdk = 23
        targetSdk = 34
        versionCode = 480
        versionName = "4.8.1"
    }

    signingConfigs {
        create("release") {
            val keyStorePath = System.getenv("MX_KEYSTORE")
            if (!keyStorePath.isNullOrBlank()) {
                storeFile = file(keyStorePath)
                storePassword = System.getenv("MX_STORE_PASS").orEmpty()
                keyAlias = System.getenv("MX_KEY_ALIAS").orEmpty()
                keyPassword = System.getenv("MX_KEY_PASS").orEmpty()
            }
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev"
        }
        release {
            if (!System.getenv("MX_KEYSTORE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
    // Persistent, network-constrained background refresh for security feeds.
    implementation("androidx.work:work-runtime:2.11.2")
    // GIF decoding for the animated GX update dialog.
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
