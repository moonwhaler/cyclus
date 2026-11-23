import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
    // open-source notices (Über Cyclus › Open-Source-Lizenzen), generated from the real dependencies at build time
    alias(libs.plugins.aboutlibraries.android)
}

android {
    namespace = "com.nordinary.cyclus"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.nordinary.cyclus"
        minSdk = 29
        targetSdk = 36
        versionCode = 10200 // +1 per release, never down (internal test builds reached 10101); see CHANGELOG
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Release signing from an untracked keystore.properties next to the project root (storeFile, storePassword,
    // keyAlias, keyPassword). Without it, release builds are unsigned and can't be installed or uploaded.
    val signing = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }
    }
    signingConfigs {
        if (signing != null) create("release") {
            storeFile = rootProject.file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (signing != null) signingConfig = signingConfigs.getByName("release")
        }
        // Release code (R8, resource shrinking, not debuggable) under the debug app's ID and key, so it installs over
        // the debug app on a test phone and keeps its data. Not for distribution.
        create("optimized") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-opt"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    // In-app language and the PDF language need every language on the device, so Play must not split by language.
    bundle {
        language { enableSplit = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

// Open-source notices: libraries come from the resolved release dependencies; aboutlibraries/ adds what POMs lack
// (SQLCipher's license text) and what isn't a dependency (the bundled fonts, OFL).
aboutLibraries {
    collect { configPath = file("aboutlibraries") }
    export { excludeFields.addAll("funding", "scm") }
}

// One startup profile for every build type (release and the optimized test build); generated on an emulator with
// ./gradlew :androidApp:generateReleaseBaselineProfile (see baselineprofile/).
baselineProfile {
    mergeIntoMain = true
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.health.connect)
    implementation(libs.kotlinx.coroutines.android)
    // installs the generated startup profile (src/release/generated/baselineProfiles) on devices without Play
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.navsuite)
    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.aboutlibraries.core)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(kotlin("test-junit"))
}

// ---- Trust rule (ANDROID_PLAN §0.1): the app never gets network access. Fails assemble/check otherwise. ----
abstract class VerifyNoNetworkPermission : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val manifest: RegularFileProperty
    @get:OutputFile abstract val marker: RegularFileProperty

    @TaskAction fun verify() {
        val text = manifest.get().asFile.readText()
        val banned = listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE", "android.permission.ACCESS_WIFI_STATE")
        val found = banned.filter { text.contains("\"$it\"") }
        if (found.isNotEmpty()) throw GradleException("Trust rule violated (ANDROID_PLAN §0.1): merged manifest requests $found")
        marker.get().asFile.writeText("no network permissions\n")
    }
}

// ---- The app's own license (GPL-3.0, shown in About Cyclus): copied from the repository's LICENSE at build time. ----
abstract class CopyAppLicense : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val license: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction fun copy() {
        license.get().asFile.copyTo(outputDir.get().file("LICENSE.txt").asFile, overwrite = true)
    }
}

val copyAppLicense = tasks.register<CopyAppLicense>("copyAppLicense") {
    license.set(rootProject.layout.projectDirectory.file("LICENSE"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(copyAppLicense, CopyAppLicense::outputDir)
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val verify = tasks.register<VerifyNoNetworkPermission>("verifyNoInternet$cap") {
            group = "verification"
            description = "Fails if the merged $cap manifest requests a network permission."
            manifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
            marker.set(layout.buildDirectory.file("verifyNoInternet/${variant.name}.txt"))
        }
        tasks.configureEach { if (name == "assemble$cap" || name == "check") dependsOn(verify) }
    }
}
