plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    jvmToolchain(21)

    // Fast host tests for the engines. iOS targets (iosArm64, iosSimulatorArm64) are added when iOS work starts;
    // commonMain stays pure Kotlin so that is a one-line change.
    jvm()

    android {
        namespace = "com.nordinary.cyclus.shared"
        compileSdk {
            version = release(37) { minorApiLevel = 2 }
        }
        minSdk = 29
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.serialization.json)
        }
        jvmTest.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
            implementation(libs.sqlcipher.android)
            implementation(libs.androidx.sqlite)
        }
        jvmTest { resources.srcDir("golden") }
        getByName("androidDeviceTest") { resources.srcDir("golden") }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
            implementation(kotlin("test-junit"))
        }
    }
}

sqldelight {
    databases {
        create("CyclusDatabase") {
            packageName.set("com.nordinary.cyclus.store.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
