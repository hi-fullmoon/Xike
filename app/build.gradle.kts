import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val ciVersionCodeValue = providers.environmentVariable("XIKE_VERSION_CODE").orNull
val ciVersionCode = ciVersionCodeValue?.toIntOrNull()
    ?: if (ciVersionCodeValue == null) {
        1
    } else {
        throw GradleException("XIKE_VERSION_CODE must be a positive integer")
    }

if (ciVersionCode < 1) {
    throw GradleException("XIKE_VERSION_CODE must be a positive integer")
}

val ciVersionName = providers.environmentVariable("XIKE_VERSION_NAME").orNull ?: "0.1.0"

val releaseStoreFile = providers.environmentVariable("XIKE_KEYSTORE_FILE").orNull
val releaseStorePassword = providers.environmentVariable("XIKE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("XIKE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("XIKE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val hasAnyReleaseSigningValue = releaseSigningValues.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigningConfig = releaseSigningValues.all { !it.isNullOrBlank() }

if (hasAnyReleaseSigningValue && !hasCompleteReleaseSigningConfig) {
    throw GradleException(
        "Release signing requires XIKE_KEYSTORE_FILE, XIKE_KEYSTORE_PASSWORD, " +
            "XIKE_KEY_ALIAS, and XIKE_KEY_PASSWORD",
    )
}

android {
    namespace = "com.xike.app"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.xike.app"
        minSdk = 26
        targetSdk = 36
        versionCode = ciVersionCode
        versionName = ciVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasCompleteReleaseSigningConfig) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasCompleteReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    jvmToolchain(17)
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.sqlite:sqlite:2.6.2")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("net.zetetic:sqlcipher-android:4.17.0@aar")

    ksp("androidx.room:room-compiler:2.8.4")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")

    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.room:room-testing:2.8.4")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
