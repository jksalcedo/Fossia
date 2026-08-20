import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
}

// Load the version.properties file
val versionPropsFile: File = rootProject.file("version.properties")
val versionProps = Properties()
if (versionPropsFile.exists()) {
    versionProps.load(FileInputStream(versionPropsFile))
} else {
    throw GradleException("version.properties file not found! Please create it in the project root.")
}

// Safely parse the values
val vMajor = versionProps.getProperty("VERSION_MAJOR", "0").toInt()
val vMinor = versionProps.getProperty("VERSION_MINOR", "1").toInt()
val vPatch = versionProps.getProperty("VERSION_PATCH", "0").toInt()
val vStage = versionProps.getProperty("VERSION_STAGE", "beta").lowercase()
val vBuild = versionProps.getProperty("VERSION_BUILD", "1").toInt()

val stageWeight = when (vStage) {
    "alpha" -> 0
    "beta" -> 1
    "rc" -> 2
    "stable" -> 3
    else -> 0
}

val suffix = if (vStage == "stable") "" else "-$vStage.$vBuild"

val computedVersionCode =
    versionProps.getProperty("VERSION_CODE")?.toInt() ?: ((vMajor * 10000000) +
            (vMinor * 100000) +
            (vPatch * 1000) +
            (stageWeight * 100) +
            vBuild)

val computedVersionName =
    versionProps.getProperty("VERSION_NAME") ?: "$vMajor.$vMinor.$vPatch$suffix"

tasks.register("updateVersionProperties") {
    description = "Updates version.properties values"

    val major = vMajor
    val minor = vMinor
    val patch = vPatch
    val weight = stageWeight
    val build = vBuild
    val sfx = suffix
    val propsFile = versionPropsFile

    doLast {
        val calculatedCode = (major * 10000000) +
                (minor * 100000) +
                (patch * 1000) +
                (weight * 100) +
                build
        val calculatedName = "$major.$minor.$patch$sfx"

        val props = Properties()
        if (propsFile.exists()) {
            props.load(propsFile.inputStream())
        }
        props.setProperty("VERSION_CODE", calculatedCode.toString())
        props.setProperty("VERSION_NAME", calculatedName)
        props.store(propsFile.outputStream(), null)
    }
}

configure<ApplicationExtension> {
    namespace = "com.jksalcedo.librefind"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jksalcedo.librefind"
        minSdk = 24
        targetSdk = 37
        versionCode = computedVersionCode
        versionName = computedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Read from environment variables
            val keystorePath = System.getenv("KEYSTORE_PATH")
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val keyAliasName = System.getenv("KEY_ALIAS")
            val keyPasswordValue = System.getenv("KEY_PASSWORD")

            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
    dependenciesInfo {
        // Disables "Dependency metadata" block in the APK signing
        includeInApk = false
        includeInBundle = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

androidComponents {
    onVariants { variant ->
        val appName = "librefind"
        val versionName = computedVersionName
        val capitalizedName = variant.name.replaceFirstChar { it.titlecase() }

        tasks.register<Copy>("rename${capitalizedName}Apk") {
            from(variant.artifacts.get(SingleArtifact.APK))
            into(layout.buildDirectory.dir("outputs/apk/${variant.name}"))
            rename(".*\\.apk", "$appName-v$versionName-${variant.name}.apk")
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)

    // Koin (Dependency Injection)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Room (Local Database)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation.graphics)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Retrofit & OkHttp (Networking)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)

    // Supabase
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    // implementation(libs.supabase.serializer.kotlinx)

    // FreeDroidWarn
    implementation(libs.freedroidwarn)

    // Ktor
    implementation(libs.ktor.client.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Navigation
    implementation(libs.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
