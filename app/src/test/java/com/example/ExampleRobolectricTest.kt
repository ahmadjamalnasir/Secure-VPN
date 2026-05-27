package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Shield VPN", appName)
  }

  @Test
  fun `main activity starts without crashing`() {
    // This will simulate the Activity lifecycle up to onResume
    // and verify the app doesn't crash on startup.
    org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup()
  }
}
