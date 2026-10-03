plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.compx551.watchos"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // Set applicationId to com.compx551.watchos for watch module
        applicationId = "com.compx551.watchos"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.protolayout)
    implementation(libs.androidx.protolayout.material3)
    implementation(libs.androidx.tiles)
    implementation(libs.androidx.tiles.tooling.preview)
    implementation(libs.androidx.watchface.complications.data.source.ktx)
    implementation(libs.androidx.health.services.client)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.wear.tooling.preview)
    implementation(libs.compose.ui.tooling)
    implementation(libs.guava)
    implementation(libs.play.services.wearable)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.tiles.renderer)
    debugImplementation(libs.androidx.tiles.tooling)
}
