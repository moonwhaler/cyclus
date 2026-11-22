package com.nordinary.cyclus.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold start into the first screen (a fresh install shows onboarding; the classes on that path — store open, SQLCipher,
 * Compose, the theme — are the startup cost). Runs on an emulator only.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class StartupProfile {
    @get:Rule val rule = BaselineProfileRule()

    @Test fun startup() = rule.collect(packageName = "com.nordinary.cyclus", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }
}
