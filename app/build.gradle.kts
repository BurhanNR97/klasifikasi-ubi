plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.klasifikasi.ubi"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.klasifikasi.ubi"
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
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.retrofit)
    implementation(libs.logging.interceptor)
    implementation(libs.okhttp3.okhttp)
    implementation(libs.converter.gson)
    implementation(libs.swiperefreshlayout)
    implementation(libs.glide)
    implementation("com.github.yalantis:ucrop:2.2.8")
    implementation("com.github.dhaval2404:imagepicker:2.1")

    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.16.1")
//    implementation("org.tensorflow:tensorflow-lite:2.13.1")
//    implementation("org.tensorflow:tensorflow-lite-select-tf-ops:2.13.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    annotationProcessor(libs.compiler)
}