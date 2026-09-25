package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.ThreatEvaluationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FocusSense", appName)
  }

  @Test
  fun `threat engine detects stranger risk`() {
    val engine = ThreatEvaluationEngine()
    val result = engine.evaluateOnDeviceMobileBert(
      appName = "Discord",
      contentTitle = "Direct Message",
      extractedText = "Hey are you alone? Don't tell your mom or dad, let's meet up at the skatepark behind school at 6pm"
    )
    assertTrue("Should be flagged", result.isFlagged)
    assertEquals("Stranger Risk", result.threatCategory)
    assertTrue("Confidence should be high", result.confidenceScore >= 0.70f)
  }

  @Test
  fun `threat engine marks educational content as safe`() {
    val engine = ThreatEvaluationEngine()
    val result = engine.evaluateOnDeviceMobileBert(
      appName = "YouTube",
      contentTitle = "Khan Academy",
      extractedText = "Introduction to cellular biology and photosynthesis in plant cells"
    )
    assertFalse("Should not be flagged", result.isFlagged)
    assertEquals("Safe", result.threatCategory)
  }
}
