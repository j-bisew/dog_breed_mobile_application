plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.woofdetect"
    compileSdk {
        version = release(36)
    }

    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.woofdetect"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true

            all {
                it.jvmArgs("-Xmx2g")

                it.testLogging {
                    events("passed", "skipped", "failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                    showStandardStreams = true
                }
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.datastore.core)
    implementation(libs.androidx.datastore.preferences.core)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // CameraX
    val cameraxVersion = "1.6.0-alpha02"
    implementation("androidx.camera:camera-core:${cameraxVersion}")
    implementation("androidx.camera:camera-camera2:${cameraxVersion}")
    implementation("androidx.camera:camera-lifecycle:${cameraxVersion}")
    implementation("androidx.camera:camera-video:${cameraxVersion}")
    implementation("androidx.camera:camera-view:${cameraxVersion}")
    implementation("androidx.camera:camera-extensions:${cameraxVersion}")

    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // OkHttp (Update to a stable version known to work well)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines (Update to the latest stable version)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")

    // Lifecycle (Align with other lifecycle dependencies)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // This one is duplicated and can be removed as it's already in libs.versions.toml
    // implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")

    // Glide (Update to the latest stable version)
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // DataStore (Align with other datastore dependencies)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ========== TESTING DEPENDENCIES ==========
    // JUnit 4 - framework do testów jednostkowych
    testImplementation("junit:junit:4.13.2")
    // Mockk - biblioteka do mockowania w Kotlin
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("io.mockk:mockk-android:1.13.8")
    // Coroutines Test - testowanie coroutines
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // Robolectric - testowanie komponentów Androida bez emulatora
    testImplementation("org.robolectric:robolectric:4.11.1")
    // AndroidX Test - biblioteki testowe
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.test.ext:junit-ktx:1.1.5")
    // Architecture Components Testing
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    // Truth - bardziej czytelne asercje (opcjonalne)
    testImplementation("com.google.truth:truth:1.1.5")
    // MockWebServer - do testowania API (opcjonalne)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}