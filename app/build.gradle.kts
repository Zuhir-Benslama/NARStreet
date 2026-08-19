import java.util.Properties
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
    jacoco
}

detekt {
    config.setFrom(rootProject.file("detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    autoCorrect = false
    toolVersion = libs.versions.detekt.get()
}

// Load local.properties if it exists
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.nars.maplibre"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nars.maplibre"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Build config fields from local.properties
        fun String.escapeBuildConfigString(): String =
            replace("\\", "\\\\").replace("\"", "\\\"")

        val apiUrl = localProperties.getProperty("NARS_API_BASE_URL", "")
        buildConfigField("String", "API_BASE_URL", "\"${apiUrl.escapeBuildConfigString()}\"")

        val sslCertHashes = localProperties.getProperty("SSL_CERT_HASHES", "")
        buildConfigField("String", "SSL_CERT_HASHES", "\"${sslCertHashes.escapeBuildConfigString()}\"")

        if (apiUrl.isBlank()) {
            val isReleaseBuild = gradle.startParameter.taskNames.any {
                it.contains("release", ignoreCase = true) || it.contains("Release")
            }
            if (isReleaseBuild) {
                throw GradleException(
                    "NARS_API_BASE_URL must be set in local.properties for release builds",
                )
            }
            println("WARNING: NARS_API_BASE_URL is not set in local.properties — the app cannot reach the backend")
        }

        val tileSatellite = localProperties.getProperty("TILE_SATELLITE", "")
        buildConfigField("String", "TILE_SATELLITE", "\"${tileSatellite.escapeBuildConfigString()}\"")

        val tileStreet = localProperties.getProperty("TILE_STREET", "")
        buildConfigField("String", "TILE_STREET", "\"${tileStreet.escapeBuildConfigString()}\"")

        val tileLight = localProperties.getProperty("TILE_LIGHT", "")
        buildConfigField("String", "TILE_LIGHT", "\"${tileLight.escapeBuildConfigString()}\"")

        val tileDark = localProperties.getProperty("TILE_DARK", "")
        buildConfigField("String", "TILE_DARK", "\"${tileDark.escapeBuildConfigString()}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.withType<Test>().configureEach {
    extensions.configure(JacocoTaskExtension::class.java) {
        isEnabled = true
    }
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    // The unit-test gate only measures code that can run on the JVM. Exclude the
    // layers that require Android instrumentation (Compose UI, Koin DI wiring,
    // EncryptedSharedPreferences crypto, framework entry points) so the threshold
    // reflects testable logic instead of permanently untestable boilerplate.
    val untestableInJvm = listOf(
        "**/ui/**",
        "**/di/**",
        "**/security/**",
        "**/MainActivity*",
        "**/NarsApplication*",
    )
    classDirectories.setFrom(
        files(
            fileTree(project.layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes").get().asFile) {
                exclude("**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*")
                exclude(untestableInJvm)
            },
            fileTree(project.layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes").get().asFile) {
                exclude("**/R.class", "**/R\$*.class", "**/BuildConfig.*", "**/Manifest*.*")
                exclude(untestableInJvm)
            },
        ),
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(project.layout.buildDirectory.get().asFile) {
            include("outputs/unit_test_code_coverage/debugUnitTest/*.exec")
            include("jacoco/*.exec")
        },
    )
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

// Release builds must ship with TLS certificate pinning configured. Pinning is
// opt-in via local.properties (SSL_CERT_HASHES) because the debug emulator
// talks to 10.0.2.2 without TLS — but a release APK with an empty pin list
// would be vulnerable to certificate substitution, so refuse to build one.
tasks.configureEach {
    if (name.startsWith("assembleRelease") || name.startsWith("bundleRelease")) {
        doFirst {
            if (localProperties.getProperty("SSL_CERT_HASHES", "").isBlank()) {
                throw GradleException(
                    "Release build requires SSL_CERT_HASHES in local.properties " +
                        "(format: hostname=sha256/hash) so the APK ships with TLS certificate pinning.",
                )
            }
        }
    }
}

dependencies {
    // MapLibre Android SDK 13.4.1 (see gradle/libs.versions.toml)
    implementation(libs.maplibre.android.sdk)

    // MapLibre Geoman Android (local module)
    implementation(project(":geoman"))

    // Kotlin
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Security - EncryptedSharedPreferences
    implementation(libs.androidx.security.crypto.ktx)

    // Koin DI
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Timber logging
    implementation(libs.timber)

    // Ktor HTTP Client
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.ktor.client.mock)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
