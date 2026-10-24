import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

val releaseProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "dev.wearjelly"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.wearjelly"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (releaseProperties.containsKey("storeFile")) {
            create("localRelease") {
                storeFile = rootProject.file(releaseProperties.getProperty("storeFile"))
                storePassword = releaseProperties.getProperty("storePassword")
                keyAlias = releaseProperties.getProperty("keyAlias")
                keyPassword = releaseProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("localRelease") ?: signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = false }
    packaging { resources.excludes += setOf("META-INF/AL2.0", "META-INF/LGPL2.1") }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true; checkReleaseBuilds = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        // Horologist 公开 API 全量 @ExperimentalHorologistApi 标注，统一 opt-in（t1 第 1.1 节）
        freeCompilerArgs.add("-opt-in=com.google.android.horologist.annotations.ExperimentalHorologistApi")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.wear.compose:compose-foundation:1.7.0")
    implementation("androidx.wear.compose:compose-material3:1.7.0")
    // Horologist M3 (t1 决策：仅 Maven Central；M3 模块必取 0.8.x-alpha，0.7.15 stable 线无 M3)
    implementation("com.google.android.horologist:horologist-media-ui-material3:0.8.4-alpha")
    implementation("com.google.android.horologist:horologist-audio-ui-material3:0.8.4-alpha")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.4.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("io.insert-koin:koin-android:3.5.6")
    implementation("io.insert-koin:koin-androidx-compose:3.5.6")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.belerweb:pinyin4j:2.5.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.room:room-testing:2.8.5")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

tasks.register("verifyNoGms") {
    group = "verification"
    description = "Reject Google Play services, Firebase, billing and wearable Play dependencies."
    doLast {
        val forbiddenGroups = listOf("com.google.android.gms", "com.google.firebase", "com.android.billingclient", "com.google.android.play", "com.google.android.support")
        val configNames = listOf("debugRuntimeClasspath", "releaseRuntimeClasspath")
        var totalVerified = 0
        for (configName in configNames) {
            val artifacts = configurations.getByName(configName).resolvedConfiguration.resolvedArtifacts
            val forbidden = artifacts.filter { artifact -> forbiddenGroups.any { artifact.moduleVersion.id.group.startsWith(it) } }
            check(forbidden.isEmpty()) { "Forbidden runtime dependencies in $configName: ${forbidden.joinToString { it.moduleVersion.id.toString() }}" }
            println("Verified ${artifacts.size} runtime artifacts in $configName: no GMS, Firebase, Play or Billing dependencies.")
            totalVerified += artifacts.size
        }
        println("Verified $totalVerified runtime artifacts across ${configNames.size} configurations: no GMS, Firebase, Play or Billing dependencies.")
    }
}
