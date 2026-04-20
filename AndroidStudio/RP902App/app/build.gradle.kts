plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val unitechRuntimeDir = rootProject.file("vendor/unitech/runtime")
val unitechRfidAar = unitechRuntimeDir.resolve("unitechRFID_v1.0.41.aar")
val unitechDeviceSdkJar = unitechRuntimeDir.resolve("UnitechSDK_1.2.19.jar")

fun buildConfigString(value: String): String =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val postgresSmokeEnabled = providers.gradleProperty("tracelinkPostgresSmoke")
    .map { it.equals("true", ignoreCase = true) }
    .getOrElse(false)
val postgresSmokeHost = providers.gradleProperty("tracelinkPostgresHost").getOrElse("10.0.2.2")
val postgresSmokePort = providers.gradleProperty("tracelinkPostgresPort").getOrElse("55432").toInt()
val postgresSmokeDatabase = providers.gradleProperty("tracelinkPostgresDatabase").getOrElse("tracelink_smoke")
val postgresSmokeUsername = providers.gradleProperty("tracelinkPostgresUsername").getOrElse("tracelink_android_app")
val postgresSmokePassword = providers.gradleProperty("tracelinkPostgresPassword").getOrElse("")
val postgresSmokeSslMode = providers.gradleProperty("tracelinkPostgresSslMode").getOrElse("Disable")
val postgresSmokeDeviceId = providers.gradleProperty("tracelinkPostgresDeviceId").getOrElse("android-local-device")
val postgresSmokeReaderType = providers.gradleProperty("tracelinkPostgresReaderType").getOrElse("RP902")

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
        debug {
            buildConfigField("boolean", "POSTGRES_SMOKE_ENABLED", postgresSmokeEnabled.toString())
            buildConfigField("String", "POSTGRES_SMOKE_HOST", buildConfigString(postgresSmokeHost))
            buildConfigField("int", "POSTGRES_SMOKE_PORT", postgresSmokePort.toString())
            buildConfigField("String", "POSTGRES_SMOKE_DATABASE", buildConfigString(postgresSmokeDatabase))
            buildConfigField("String", "POSTGRES_SMOKE_USERNAME", buildConfigString(postgresSmokeUsername))
            buildConfigField("String", "POSTGRES_SMOKE_PASSWORD", buildConfigString(postgresSmokePassword))
            buildConfigField("String", "POSTGRES_SMOKE_SSL_MODE", buildConfigString(postgresSmokeSslMode))
            buildConfigField("String", "POSTGRES_SMOKE_DEVICE_ID", buildConfigString(postgresSmokeDeviceId))
            buildConfigField("String", "POSTGRES_SMOKE_READER_TYPE", buildConfigString(postgresSmokeReaderType))
        }
        release {
            isMinifyEnabled = false
            buildConfigField("boolean", "POSTGRES_SMOKE_ENABLED", "false")
            buildConfigField("String", "POSTGRES_SMOKE_HOST", buildConfigString(""))
            buildConfigField("int", "POSTGRES_SMOKE_PORT", "5432")
            buildConfigField("String", "POSTGRES_SMOKE_DATABASE", buildConfigString(""))
            buildConfigField("String", "POSTGRES_SMOKE_USERNAME", buildConfigString(""))
            buildConfigField("String", "POSTGRES_SMOKE_PASSWORD", buildConfigString(""))
            buildConfigField("String", "POSTGRES_SMOKE_SSL_MODE", buildConfigString("Require"))
            buildConfigField("String", "POSTGRES_SMOKE_DEVICE_ID", buildConfigString("android-local-device"))
            buildConfigField("String", "POSTGRES_SMOKE_READER_TYPE", buildConfigString("RP902"))
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
        buildConfig = true
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
