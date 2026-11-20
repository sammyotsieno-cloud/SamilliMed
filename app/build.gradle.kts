plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
}

android {
    // One fixed signing key on every build, so a new build installs over the old one.
    signingConfigs {
        create("fixed") {
            val keystorePath = System.getenv("SM_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SM_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SM_KEY_ALIAS")
                keyPassword = System.getenv("SM_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            if (!System.getenv("SM_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("fixed")
            }
        }
    }
    namespace = "org.samillimed.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.samillimed.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Firebase: the BoM picks matching versions for the libraries below
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
