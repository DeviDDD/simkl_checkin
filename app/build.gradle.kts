plugins {
    id("com.android.application")
}

// Supplied by the GitHub Actions workflow; safe defaults for local builds.
val ciVersion = (System.getenv("VERSION_CODE") ?: "1").toInt()
val keystorePath: String? = System.getenv("KEYSTORE_FILE")
val githubRepo = System.getenv("GITHUB_REPOSITORY") ?: ""

android {
    namespace = "app.simklcheckin"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.simklcheckin"
        minSdk = 29
        targetSdk = 35
        versionCode = ciVersion
        versionName = "1.$ciVersion"
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "simkl"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
}
