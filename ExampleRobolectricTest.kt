package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PermissionManagerHelper
import com.example.data.SystemTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Apex Boost", appName)
    }

    @Test
    fun `telemetry repository reads valid device metrics`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SystemTelemetryRepository(context)
        val snapshot = repo.readSnapshot()
        assertTrue(snapshot.cpuCoreCount >= 1)
        assertTrue(snapshot.displayRefreshRateHz >= 1f)
    }

    @Test
    fun `permission helper checks permissions safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = PermissionManagerHelper(context)
        val status = helper.checkAll()
        assertTrue(status.coreGrantedCount in 0..5)
    }
}
