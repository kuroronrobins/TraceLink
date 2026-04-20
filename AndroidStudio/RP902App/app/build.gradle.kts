plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val unitechRuntimeDir = rootProject.file("vendor/unitech/runtime")
val unitechRfidAar = unitechRuntimeDir.resolve("unitechRFID_v1.0.41.aar")
val unitechDeviceSdkJar = unitechRuntimeDir.resolve("UnitechSDK_1.2.19.jar")

check(unitechRfidAar.isFile) {
    "Missing Unitech runtime AAR: ${unitechRfidAar.path}"
}
check(unitechDeviceSdkJar.isFile) {
    "Missing Unitech device SDK JAR: ${unitechDeviceSdkJar.path}"
}

android {
    namespace = "jp.co.terumo.tracelink.rp902app"
    compileSdk = 35

    defaultConfig {
        applicationId = "jp.co.terumo.tracelink.rp902app"
        minSdk = 28
        targetSdk = 35
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.postgres.jdbc)
    implementation(files(unitechRfidAar, unitechDeviceSdkJar))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
